package com.pvpvisuals.render.kill;

/** Заранее выделенный буфер квадов (позиции относительно камеры + ARGB на вершину). Без аллокаций в кадре. */
final class QuadBuffer {
	static final int MAX_QUADS = 6000;

	final float[] pos = new float[MAX_QUADS * 4 * 3];
	final int[] col = new int[MAX_QUADS * 4];
	int quads;

	void clear() {
		quads = 0;
	}

	void quad(float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
			float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4) {
		if (quads >= MAX_QUADS || ((c1 | c2 | c3 | c4) >>> 24) == 0) {
			return;
		}
		int p = quads * 12;
		int c = quads * 4;
		pos[p] = x1; pos[p + 1] = y1; pos[p + 2] = z1; col[c] = c1;
		pos[p + 3] = x2; pos[p + 4] = y2; pos[p + 5] = z2; col[c + 1] = c2;
		pos[p + 6] = x3; pos[p + 7] = y3; pos[p + 8] = z3; col[c + 2] = c3;
		pos[p + 9] = x4; pos[p + 10] = y4; pos[p + 11] = z4; col[c + 3] = c4;
		quads++;
	}
}
