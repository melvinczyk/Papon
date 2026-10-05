#!/usr/bin/env python3
"""Compacts Java source without changing what it does: a block whose body is exactly one simple statement on one line

    <header> {            if (x) {               public int foo() {
        <statement>;          return;                return bar;
    }                     }                      }

becomes one line, `<header> { <statement>; }`, when the result fits in MAX columns. Only plain headers (methods, if, else,
for, while, do-less loops, case-less) are joined: never a class/enum/record/interface body, a switch, a lambda, an anonymous
class, an array initialiser, a static block, try/catch/finally, or anything with a comment, a string spanning lines, or a
label in it. The statement must be one line ending in ';' with no '{' or '}'. Run over files given on the command line;
--check only reports.
"""
import re
import sys

MAX = 150
HEADER_BAD = re.compile(r'\b(class|enum|record|interface|switch|try|catch|finally|static|synchronized|do)\b|->|=\s*\{$|new\s+[\w.<>\[\], ?]+\([^)]*\)\s*\{$|^\s*\{$|"')
STMT_BAD = re.compile(r'[{}]|//|/\*|\*/|^\s*(case|default)\b|^\s*\w+:\s|"""')


def compact(lines):
    out, i, saved = [], 0, 0
    while i < len(lines):
        if i + 2 < len(lines):
            h, b, c = lines[i].rstrip('\n'), lines[i + 1].rstrip('\n'), lines[i + 2].rstrip('\n')
            ind = len(h) - len(h.lstrip())
            if (h.endswith(' {') and not HEADER_BAD.search(h.strip()) and '//' not in h
                    and c.strip() == '}' and len(c) - len(c.lstrip()) == ind
                    and b.strip().endswith(';') and not STMT_BAD.search(b.strip())
                    and len(b) - len(b.lstrip()) > ind and b.count('"') % 2 == 0):
                joined = h + ' ' + b.strip() + ' }'
                if len(joined) <= MAX:
                    out.append(joined + '\n')
                    i += 3
                    saved += 2
                    continue
        out.append(lines[i])
        i += 1
    return out, saved


if __name__ == '__main__':
    check = '--check' in sys.argv
    total = 0
    for p in [a for a in sys.argv[1:] if not a.startswith('--')]:
        lines = open(p, encoding='utf-8').readlines()
        out, saved = compact(lines)
        total += saved
        if saved and not check:
            open(p, 'w', encoding='utf-8', newline='\n').writelines(out)
    print('lines saved:', total)
