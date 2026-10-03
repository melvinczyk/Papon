"""Chef Papon's cloned voice, on a Modal GPU (profile melvinczyk): Qwen3-TTS-12Hz-1.7B-Base clones the
reference recording, Whisper checks every take. Driven by tools/gen_boss_voice.py, which calls these
functions through `modal run`; nothing here runs on the Mac.

  transcribe(audio)                    Whisper large-v3 on the reference: the text, with word timings
  clone(ref, ref_text, lines, takes)   `takes` takes of every line (different seeds), each transcribed
                                       back so the caller can keep the one that says the line best
"""
import io

import modal

app = modal.App('papon-voice')

image = (
    modal.Image.debian_slim(python_version='3.12')
    .apt_install('ffmpeg', 'sox', 'libsox-dev')
    .pip_install('qwen-tts', 'openai-whisper', 'soundfile', 'numpy')
)
cache = modal.Volume.from_name('papon-voice-cache', create_if_missing=True)

TTS_MODEL = 'Qwen/Qwen3-TTS-12Hz-1.7B-Base'


def _whisper():
    import os
    os.environ.setdefault('HF_HOME', '/data/hf')
    import whisper
    return whisper.load_model('large-v3', download_root='/data/whisper')


def _wav_bytes(data, sr):
    import soundfile
    buf = io.BytesIO()
    soundfile.write(buf, data, sr, format='WAV', subtype='PCM_16')
    return buf.getvalue()


def _load(audio_bytes):
    """Any format ffmpeg reads, to mono float32 at 24 kHz."""
    import subprocess
    import numpy as np
    out = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', 'pipe:0', '-ac', '1', '-ar', '24000', '-f', 'f32le', 'pipe:1'],
                         input=audio_bytes, capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).copy(), 24000


@app.function(image=image, gpu='L40S', volumes={'/data': cache}, timeout=1800)
def transcribe(audio_bytes: bytes, language: str = None) -> dict:
    import tempfile
    model = _whisper()
    with tempfile.NamedTemporaryFile(suffix='.audio') as f:
        f.write(audio_bytes)
        f.flush()
        r = model.transcribe(f.name, language=language, word_timestamps=True)
    cache.commit()
    return {'text': r['text'], 'language': r['language'],
            'segments': [{'start': s['start'], 'end': s['end'], 'text': s['text'],
                          'words': [{'w': w['word'], 's': w['start'], 'e': w['end']} for w in s.get('words', [])]}
                         for s in r['segments']]}


@app.function(image=image, gpu='L40S', volumes={'/data': cache}, timeout=3600)
def clone(refs: dict, lines: list, takes: int = 4) -> dict:
    """refs: {name: [audio bytes, transcript]}, the voices to clone (the same speaker, read calm or worked
    up: the clone copies the delivery of the clip it is given).
    lines: [{'id', 'text', 'ref', 'instruct' (optional), 'gen' (optional sampling kwargs)}].
    Returns {id: [{'wav': bytes, 'heard': str, 'seconds': float, 'sim': float}]}."""
    import os
    os.environ['HF_HOME'] = '/data/hf'
    import tempfile
    import numpy as np
    import torch
    from qwen_tts import Qwen3TTSModel

    model = Qwen3TTSModel.from_pretrained(TTS_MODEL, device_map='cuda:0', dtype=torch.bfloat16, attn_implementation='sdpa')
    stt = _whisper()

    def embedding(p):
        # the speaker vector out of a clone prompt (x-vector mode), for comparing takes with the reference
        items = p if isinstance(p, (list, tuple)) else [p]
        it = items[0]
        fields = it.items() if isinstance(it, dict) else ((n, getattr(it, n, None)) for n in dir(it) if not n.startswith('__'))
        for name, v in fields:
            if torch.is_tensor(v) and ('spk' in name or 'speaker' in name or 'xvec' in name or 'embed' in name):
                return v.detach().float().flatten().cpu()
        return None

    prompts, embs = {}, {}
    for name, (audio, text) in refs.items():
        ref = _load(audio)
        prompts[name] = model.create_voice_clone_prompt(ref_audio=ref, ref_text=text, x_vector_only_mode=False)
        embs[name] = embedding(model.create_voice_clone_prompt(ref_audio=ref, x_vector_only_mode=True))
    out = {}
    for line in lines:
        out[line['id']] = []
        gen = dict(line.get('gen') or {})
        if line.get('instruct'):
            # the Base model's generate takes an instruction prompt too (the VoiceDesign/CustomVoice models are
            # trained on them): a nudge toward the delivery
            gen['instruct_ids'] = [model._tokenize_texts([model._build_instruct_text(line['instruct'])])[0]]
        for k in range(takes):
            torch.manual_seed(1000 * k + 7)
            np.random.seed(1000 * k + 7)
            wavs, sr = model.generate_voice_clone(text=line['text'], language=line.get('language', 'English'),
                                                  voice_clone_prompt=prompts[line['ref']], **gen)
            wav = np.asarray(wavs[0], dtype=np.float32)
            with tempfile.NamedTemporaryFile(suffix='.wav') as f:
                f.write(_wav_bytes(wav, sr))
                f.flush()
                heard = stt.transcribe(f.name, language='en')['text'].strip()
            sim = None
            if embs[line['ref']] is not None:
                e = embedding(model.create_voice_clone_prompt(ref_audio=(wav, sr), x_vector_only_mode=True))
                sim = float(torch.nn.functional.cosine_similarity(e, embs[line['ref']], dim=0))
            out[line['id']].append({'wav': _wav_bytes(wav, sr), 'heard': heard, 'seconds': len(wav) / sr, 'sim': sim})
            print(line['id'], k, round(len(wav) / sr, 2), sim and round(sim, 3), '|', heard, flush=True)
    cache.commit()
    return out
