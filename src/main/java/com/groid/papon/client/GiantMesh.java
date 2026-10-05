package com.groid.papon.client;

import java.util.ArrayList;
import java.util.List;

/**
 * El Gigante's sugar skull and jaw as smooth meshes (2026-10-05; it was a hologram of voxels, the user: "flat... it needs to feel
 * like a massive, three-dimensional physical presence... more convincing skeletal structure"). Each is a signed distance field
 * (a cranium, the face and the cheekbones melted into it, the brow ridge, temples pressed in, deep eye sockets, the nasal
 * opening, a row of teeth; the jaw a horseshoe of bone up to its hinges, its own teeth) turned into quads once, when the class
 * loads, by surface nets: a vertex in every cell the surface crosses, a quad across every grid edge it crosses, the normal the
 * field's gradient. Each vertex carries its colour: old bone, darker where the bone folds in (the field sampled a little way
 * out along the normal: ambient occlusion baked in), the sugar skull's petals of pink and marigold round the sockets and on the
 * brow, white teeth, black sockets. Units are the skull's (SpiritGiantEntity.V blocks each): x along its right(), y up, z forward.
 */
final class GiantMesh {
    /** A mesh: per vertex x, y, z and the normal, and an ARGB colour; quads as 4 vertex indices each. */
    static final class Mesh {
        final float[] pos, nrm;
        final int[] col;
        final int[] quads;

        Mesh(float[] pos, float[] nrm, int[] col, int[] quads) {
            this.pos = pos;
            this.nrm = nrm;
            this.col = col;
            this.quads = quads;
        }
    }

    interface Field {
        float at(float x, float y, float z);
    }

    interface Paint {
        int at(float x, float y, float z, float ao);
    }

    /** The jaw's hinge (skull units): it opens about the x axis through here. */
    static final float HINGE_Y = -5.6F, HINGE_Z = -0.8F;

    static final Mesh SKULL = build(GiantMesh::skull, -10, 10, -11, 10.5F, -9.5F, 10, 0.55F, GiantMesh::paintSkull);
    static final Mesh JAW = build(GiantMesh::jaw, -7.5F, 7.5F, -11, -4, -3, 8.5F, 0.48F, GiantMesh::paintJaw);

    private GiantMesh() {}

    // ------------------------------------------------------------------ distance functions

    private static float len(float x, float y, float z) { return (float) Math.sqrt(x * x + y * y + z * z); }

    private static float ellipsoid(float x, float y, float z, float rx, float ry, float rz) {
        float k0 = len(x / rx, y / ry, z / rz);
        float k1 = len(x / (rx * rx), y / (ry * ry), z / (rz * rz));
        return k1 < 1e-6F ? -Math.min(rx, Math.min(ry, rz)) : k0 * (k0 - 1) / k1;
    }

    private static float capsule(float x, float y, float z, float ax, float ay, float az, float bx, float by, float bz, float r) {
        float px = x - ax, py = y - ay, pz = z - az, dx = bx - ax, dy = by - ay, dz = bz - az;
        float h = Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / (dx * dx + dy * dy + dz * dz)));
        return len(px - dx * h, py - dy * h, pz - dz * h) - r;
    }

    private static float roundBox(float x, float y, float z, float bx, float by, float bz, float r) {
        float qx = Math.abs(x) - bx + r, qy = Math.abs(y) - by + r, qz = Math.abs(z) - bz + r;
        return len(Math.max(qx, 0), Math.max(qy, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, Math.max(qy, qz)), 0) - r;
    }

    /** Smooth union. */
    private static float smin(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25F;
    }

    /** Smooth subtraction of b from a. */
    private static float cut(float a, float b, float k) { return -smin(-a, b, k); }

    private static float socket(float x, float y, float z, int s) { return ellipsoid(x - s * 3.3F, y + 0.5F, z - 7.6F, 2.45F, 2.35F, 3.9F); }

    private static float nose(float x, float y, float z) {
        // an upside-down heart: two lobes side by side at the top, narrowing down
        float a = ellipsoid(x - 0.55F, y + 2.9F, z - 7.6F, 0.85F, 1.3F, 2.6F);
        float b = ellipsoid(x + 0.55F, y + 2.9F, z - 7.6F, 0.85F, 1.3F, 2.6F);
        return Math.min(a, b);
    }

    private static float upperTeeth(float x, float y, float z) {
        float d = 1e9F;
        for (int i = -5; i <= 5; i++) {
            float tx = i * 0.94F, tz = 6.4F - 0.072F * tx * tx;
            d = Math.min(d, roundBox(x - tx, y + 6.75F, z - tz, 0.4F, 0.85F, 0.46F, 0.18F));
        }
        return d;
    }

    static float skull(float x, float y, float z) {
        float d = ellipsoid(x, y - 1.6F, z + 0.6F, 7.9F, 7.4F, 8.4F);
        d = smin(d, roundBox(x, y + 3.7F, z - 2.7F, 5.0F, 2.5F, 4.5F, 1.6F), 2.4F);               // the face
        for (int s = -1; s <= 1; s += 2) {
            d = smin(d, capsule(x, y, z, s * 4.3F, -2.5F, 5.4F, s * 7.1F, -2.1F, 1.0F, 1.1F), 1.1F);   // cheekbones
            d = cut(d, len(x - s * 8.9F, y + 1.0F, z - 1.8F) - 3.0F, 1.3F);                          // temples
        }
        d = smin(d, capsule(x, y, z, -5.3F, 1.5F, 6.4F, 5.3F, 1.5F, 6.4F, 0.95F), 1.7F);             // the brow ridge
        for (int s = -1; s <= 1; s += 2) d = cut(d, socket(x, y, z, s), 0.6F);
        d = cut(d, nose(x, y, z), 0.35F);
        // the mouth: the face's underside cut back a little behind the teeth
        d = cut(d, roundBox(x, y + 6.9F, z - 5.0F, 4.6F, 0.7F, 2.6F, 0.4F), 0.5F);
        return Math.min(d, upperTeeth(x, y, z));
    }

    private static float lowerTeeth(float x, float y, float z) {
        float d = 1e9F;
        for (int i = -4; i <= 4; i++) {
            float tx = i * 0.94F, tz = 6.0F - 0.08F * tx * tx;
            d = Math.min(d, roundBox(x - tx, y + 7.75F, z - tz, 0.38F, 0.7F, 0.42F, 0.16F));
        }
        return d;
    }

    static float jaw(float x, float y, float z) {
        // the horseshoe of the mandible, the chin, the rami up to the hinge
        float[][] arc = {{-5.0F, -8.3F, 0.6F}, {-4.2F, -8.9F, 3.9F}, {-2.2F, -9.3F, 5.9F}, {0, -9.45F, 6.4F}, {2.2F, -9.3F, 5.9F},
                {4.2F, -8.9F, 3.9F}, {5.0F, -8.3F, 0.6F}};
        float d = 1e9F;
        for (int i = 0; i + 1 < arc.length; i++) {
            d = smin(d, capsule(x, y, z, arc[i][0], arc[i][1], arc[i][2], arc[i + 1][0], arc[i + 1][1], arc[i + 1][2], 1.0F), 0.6F);
        }
        d = smin(d, ellipsoid(x, y + 9.5F, z - 6.4F, 1.6F, 1.1F, 0.9F), 0.8F);                       // the chin
        for (int s = -1; s <= 1; s += 2) {
            d = smin(d, capsule(x, y, z, s * 5.0F, -8.3F, 0.6F, s * 5.5F, HINGE_Y, HINGE_Z, 0.85F), 0.6F);
            d = smin(d, ellipsoid(x - s * 5.5F, y - HINGE_Y, z - HINGE_Z, 0.95F, 0.8F, 1.1F), 0.4F);   // the condyles
        }
        return Math.min(d, lowerTeeth(x, y, z));
    }

    // ------------------------------------------------------------------ colour

    private static int rgb(float r, float g, float b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    /** Old bone, a little warm, greyer and bluer where it folds in. */
    private static int bone(float ao) {
        float k = 0.42F + 0.58F * ao;
        return rgb((236 * k) + (1 - ao) * 8, (229 * k) + (1 - ao) * 14, (210 * k) + (1 - ao) * 40);
    }

    private static int paintSkull(float x, float y, float z, float ao) {
        if (upperTeeth(x, y, z) < 0.12F) return rgb(250 * (0.6F + 0.4F * ao), 248 * (0.6F + 0.4F * ao), 238 * (0.6F + 0.4F * ao));
        for (int s = -1; s <= 1; s += 2) {
            float in = socket(x, y, z, s);
            if (in > -0.25F && in < 0.35F && z < 8.6F) return rgb(20, 16, 30);              // down in the socket: black
        }
        if (nose(x, y, z) < 0.35F) return rgb(26, 20, 34);
        if (z > 4.2F) {
            // the petals round each socket, pink and marigold by turns
            for (int s = -1; s <= 1; s += 2) {
                float dx = x - s * 3.3F, dy = y + 0.5F;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                if (r > 2.45F && r < 3.75F) {
                    double ang = Math.atan2(dy, dx);
                    float petal = (float) Math.abs(Math.sin(ang * 4));
                    if (r < 2.85F + 0.8F * petal) {
                        float k = 0.55F + 0.45F * ao;
                        return Math.floorMod((int) Math.floor(ang / (Math.PI / 4)), 2) == 0 ? rgb(255 * k, 64 * k, 170 * k) : rgb(255 * k, 160 * k, 30 * k);
                    }
                }
            }
            // the flower on its brow
            float fx = x, fy = y - 4.6F;
            float fr = (float) Math.sqrt(fx * fx + fy * fy);
            if (fr < 0.9F) return rgb(255 * (0.6F + 0.4F * ao), 70, 160);
            if (fr < 2.3F) {
                double ang = Math.atan2(fy, fx);
                if (fr < 1.3F + 1.0F * Math.abs(Math.sin(ang * 2.5))) return rgb(255 * (0.6F + 0.4F * ao), 168 * (0.6F + 0.4F * ao), 34);
            }
            // a blue swirl down each cheekbone
            for (int s = -1; s <= 1; s += 2) {
                float cd = capsule(x, y, z, s * 4.3F, -2.5F, 5.4F, s * 6.4F, -2.2F, 2.4F, 1.1F);
                if (cd < 0.15F && cd > -0.6F && y > -2.9F && y < -1.7F) return rgb(70 * (0.6F + 0.4F * ao), 205 * (0.6F + 0.4F * ao), 255 * (0.6F + 0.4F * ao));
            }
            // dots of marigold along the brow's top
            for (int i = -3; i <= 3; i++) {
                if (i == 0) continue;
                float dx = x - i * 1.7F, dy = y - 2.9F + Math.abs(i) * 0.12F;
                if (dx * dx + dy * dy < 0.18F) return rgb(255, 176, 40);
            }
        }
        return bone(ao);
    }

    private static int paintJaw(float x, float y, float z, float ao) {
        if (lowerTeeth(x, y, z) < 0.12F) return rgb(250 * (0.6F + 0.4F * ao), 248 * (0.6F + 0.4F * ao), 238 * (0.6F + 0.4F * ao));
        // a ring of little marigold dots round the chin
        float dx = x, dz = z - 6.6F, dy = y + 9.4F;
        if (Math.abs(dy) < 0.6F && dz > -0.6F) {
            for (int i = -3; i <= 3; i++) {
                float px = i * 1.15F;
                if ((dx - px) * (dx - px) + (dy) * (dy) < 0.16F) return rgb(255, 160, 40);
            }
        }
        return bone(ao);
    }

    // ------------------------------------------------------------------ surface nets

    private static Mesh build(Field f, float x0, float x1, float y0, float y1, float z0, float z1, float h, Paint paint) {
        int nx = (int) Math.ceil((x1 - x0) / h) + 1, ny = (int) Math.ceil((y1 - y0) / h) + 1, nz = (int) Math.ceil((z1 - z0) / h) + 1;
        float[] v = new float[nx * ny * nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) v[(i * ny + j) * nz + k] = f.at(x0 + i * h, y0 + j * h, z0 + k * h);
            }
        }
        int[] cell = new int[(nx - 1) * (ny - 1) * (nz - 1)];
        java.util.Arrays.fill(cell, -1);
        List<float[]> verts = new ArrayList<>();
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        float[] cv = new float[8];
        for (int i = 0; i < nx - 1; i++) {
            for (int j = 0; j < ny - 1; j++) {
                for (int k = 0; k < nz - 1; k++) {
                    int in = 0;
                    for (int c = 0; c < 8; c++) {
                        cv[c] = v[((i + (c & 1)) * ny + j + ((c >> 1) & 1)) * nz + k + ((c >> 2) & 1)];
                        if (cv[c] < 0) in++;
                    }
                    if (in == 0 || in == 8) continue;
                    float sx = 0, sy = 0, sz = 0;
                    int n = 0;
                    for (int[] e : edges) {
                        float a = cv[e[0]], b = cv[e[1]];
                        if ((a < 0) == (b < 0)) continue;
                        float tt = a / (a - b);
                        float ax = e[0] & 1, ay = (e[0] >> 1) & 1, az = (e[0] >> 2) & 1;
                        float bx = e[1] & 1, by = (e[1] >> 1) & 1, bz = (e[1] >> 2) & 1;
                        sx += ax + (bx - ax) * tt;
                        sy += ay + (by - ay) * tt;
                        sz += az + (bz - az) * tt;
                        n++;
                    }
                    cell[(i * (ny - 1) + j) * (nz - 1) + k] = verts.size();
                    verts.add(new float[]{x0 + (i + sx / n) * h, y0 + (j + sy / n) * h, z0 + (k + sz / n) * h});
                }
            }
        }
        List<int[]> quads = new ArrayList<>();
        // a quad across every grid edge the surface crosses, joining the four cells round it
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    float a = v[(i * ny + j) * nz + k];
                    for (int axis = 0; axis < 3; axis++) {
                        int i2 = i + (axis == 0 ? 1 : 0), j2 = j + (axis == 1 ? 1 : 0), k2 = k + (axis == 2 ? 1 : 0);
                        if (i2 >= nx || j2 >= ny || k2 >= nz) continue;
                        float b = v[(i2 * ny + j2) * nz + k2];
                        if ((a < 0) == (b < 0)) continue;
                        int[][] around;
                        if (axis == 0) around = new int[][]{{i, j - 1, k - 1}, {i, j, k - 1}, {i, j, k}, {i, j - 1, k}};
                        else if (axis == 1) around = new int[][]{{i - 1, j, k - 1}, {i - 1, j, k}, {i, j, k}, {i, j, k - 1}};
                        else around = new int[][]{{i - 1, j - 1, k}, {i, j - 1, k}, {i, j, k}, {i - 1, j, k}};
                        int[] q = new int[4];
                        boolean ok = true;
                        for (int c = 0; c < 4; c++) {
                            int ci = around[c][0], cj = around[c][1], ck = around[c][2];
                            if (ci < 0 || cj < 0 || ck < 0 || ci >= nx - 1 || cj >= ny - 1 || ck >= nz - 1) {
                                ok = false;
                                break;
                            }
                            q[c] = cell[(ci * (ny - 1) + cj) * (nz - 1) + ck];
                            if (q[c] < 0) ok = false;
                        }
                        if (!ok) continue;
                        if (a < 0) quads.add(new int[]{q[0], q[1], q[2], q[3]});
                        else quads.add(new int[]{q[3], q[2], q[1], q[0]});
                    }
                }
            }
        }
        int n = verts.size();
        float[] pos = new float[n * 3], nrm = new float[n * 3];
        int[] col = new int[n];
        float e = h * 0.5F;
        for (int i = 0; i < n; i++) {
            float[] p = verts.get(i);
            float gx = f.at(p[0] + e, p[1], p[2]) - f.at(p[0] - e, p[1], p[2]);
            float gy = f.at(p[0], p[1] + e, p[2]) - f.at(p[0], p[1] - e, p[2]);
            float gz = f.at(p[0], p[1], p[2] + e) - f.at(p[0], p[1], p[2] - e);
            float gl = Math.max(1e-6F, len(gx, gy, gz));
            gx /= gl;
            gy /= gl;
            gz /= gl;
            pos[i * 3] = p[0];
            pos[i * 3 + 1] = p[1];
            pos[i * 3 + 2] = p[2];
            nrm[i * 3] = gx;
            nrm[i * 3 + 1] = gy;
            nrm[i * 3 + 2] = gz;
            // ambient occlusion: how open the space is a little way out from the surface
            float o = 0;
            for (float s : new float[]{0.6F, 1.4F, 2.6F}) o += Math.max(0, Math.min(1, f.at(p[0] + gx * s, p[1] + gy * s, p[2] + gz * s) / s));
            float ao = Math.max(0.25F, Math.min(1, o / 3F));
            col[i] = paint.at(p[0], p[1], p[2], ao);
        }
        int[] qs = new int[quads.size() * 4];
        for (int i = 0; i < quads.size(); i++) System.arraycopy(quads.get(i), 0, qs, i * 4, 4);
        return new Mesh(pos, nrm, col, qs);
    }
}
