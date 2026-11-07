package com.pvpvisuals.gui.smooth;

import org.joml.Matrix3x2f;

import com.pvpvisuals.config.PvpConfig;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Плавный рендер GUI без шейдеров и текстур: скруглённые прямоугольники, круги, кольца, обводки, градиенты, свечение,
 * линии. Всё строится как сетка треугольников с цветом в вершинах и отправляется в GUI-рендер Minecraft как собственный
 * {@link GuiElementRenderState} (тот же механизм, что у встроенных элементов GUI и у теста Fabric API
 * {@code GuiRendererNonQuadsTest}).
 *
 * <h3>Математика</h3>
 * <ul>
 *   <li><b>Скругление.</b> Контур — прямоугольник с четырьмя дугами радиуса r. Каждая дуга режется на n сегментов,
 *       n зависит от радиуса в физических пикселях ({@link #segments}), максимум 16. Контур со смещением {@code off}
 *       (кривая равного расстояния от исходного контура) — тот же прямоугольник с радиусом r + off, поэтому вершины
 *       разных смещений соответствуют друг другу один к одному, и между ними легко строить полосы.</li>
 *   <li><b>Сглаживание краёв (геометрический AA).</b> Заливка идёт «веером» от центра до контура со смещением −f/2
 *       (альфа 100%), а затем полоса шириной f до смещения +f/2, где альфа падает до 0. f — ровно один физический
 *       пиксель экрана (1 / guiScale в единицах GUI). Видеокарта линейно интерполирует альфу по полосе, получается
 *       мягкий край с покрытием 50% ровно на номинальной границе — это то же, что даёт SDF-шейдер со smoothstep
 *       шириной в 1 пиксель.</li>
 *   <li><b>Обводка.</b> Полоса между смещениями −t и 0 (внутрь), с такими же мягкими краями; толщина меньше пикселя
 *       превращается в «волосок»: ширина f, альфа умножена на t/f (так 1.5 px и 2 px получают честные полупрозрачные
 *       края).</li>
 *   <li><b>Градиент.</b> Цвет считается в каждой вершине по билинейной интерполяции четырёх угловых цветов
 *       ({@link Paint}); дальше линейную интерполяцию делает видеокарта, ступенек нет.</li>
 *   <li><b>Свечение.</b> Несколько полос наружу, альфа в вершинах падает как (1 − k/n)², между ними линейно.</li>
 * </ul>
 *
 * <p>Все координаты — в единицах GUI (float), текущая матрица {@code g.pose()} и текущий scissor учитываются.
 * Цвета — ARGB. Класс ничего не знает о глобальной прозрачности — передавайте уже умноженные цвета.
 */
public final class SmoothRenderer {
	private SmoothRenderer() { }

	/** Включено ли сглаживание: config/pvpvisuals.json -> gui.smoothGui (по умолчанию true). */
	public static boolean isOn() {
		return PvpConfig.get().gui.smoothGui;
	}

	// ---------- конвейер (pipeline) ----------

	/**
	 * Собственный pipeline на базе GUI_SNIPPET (проекция, blend translucent, без depth). Формат вершин POSITION_COLOR,
	 * режим TRIANGLES; withUsePipelineDrawModeForGui — расширение Fabric API, без него GUI-рендер считает, что любые
	 * вершины — это QUADS. Cull выключен, чтобы порядок обхода треугольников не имел значения.
	 */
	private static final class Pipelines {
		static final RenderPipeline SMOOTH = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/smooth_gui"))
				.withUsePipelineDrawModeForGui(true)
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
				.withCull(false)
				.build();
	}

	// ---------- публичное API ----------

	/** Заливка скруглённого прямоугольника вертикальным градиентом top -> bottom. */
	public static void drawSmoothRoundedRect(GuiGraphics g, float x, float y, float w, float h, float r,
			int top, int bottom) {
		drawSmoothRoundedRect(g, x, y, w, h, r, top, bottom, 0, 0f);
	}

	/** Заливка + обводка внутрь толщиной {@code border} (0 — без обводки). */
	public static void drawSmoothRoundedRect(GuiGraphics g, float x, float y, float w, float h, float r,
			int top, int bottom, int borderColor, float border) {
		if (w <= 0 || h <= 0) {
			return;
		}
		float f = feather();
		Shape s = new Shape(x, y, w, h, r);
		Mesh m = new Mesh();
		if (alpha(top) > 0 || alpha(bottom) > 0) {
			fill(m, s, new Paint(x, y, w, h, top, top, bottom, bottom), f);
		}
		if (border > 0f && alpha(borderColor) > 0) {
			stroke(m, s, new Paint(x, y, w, h, borderColor, borderColor, borderColor, borderColor), -border / 2f, border, f);
		}
		submit(g, m);
	}

	/** Заливка произвольным четырёхугольным градиентом: цвета углов TL, TR, BL, BR. */
	public static void drawSmoothGradient(GuiGraphics g, float x, float y, float w, float h, float r,
			int tl, int tr, int bl, int br) {
		if (w <= 0 || h <= 0) {
			return;
		}
		Mesh m = new Mesh();
		fill(m, new Shape(x, y, w, h, r), new Paint(x, y, w, h, tl, tr, bl, br), feather());
		submit(g, m);
	}

	/** Только обводка (внутрь от границы) толщиной t. */
	public static void drawSmoothBorder(GuiGraphics g, float x, float y, float w, float h, float r, int color,
			float t) {
		if (w <= 0 || h <= 0 || t <= 0 || alpha(color) == 0) {
			return;
		}
		Mesh m = new Mesh();
		stroke(m, new Shape(x, y, w, h, r), new Paint(x, y, w, h, color, color, color, color), -t / 2f, t, feather());
		submit(g, m);
	}

	/** Мягкое свечение снаружи скруглённого прямоугольника: spread — ширина в единицах GUI. */
	public static void drawSmoothGlow(GuiGraphics g, float x, float y, float w, float h, float r, int color,
			float spread) {
		if (w <= 0 || h <= 0 || spread <= 0f || alpha(color) == 0) {
			return;
		}
		Shape s = new Shape(x, y, w, h, r);
		Paint p = new Paint(x, y, w, h, color, color, color, color);
		Mesh m = new Mesh();
		int steps = 8;
		float[] prev = s.ring(0f);
		for (int k = 1; k <= steps; k++) {
			float t = k / (float) steps;
			float[] cur = s.ring(spread * t);
			float a0 = sq(1f - (k - 1) / (float) steps);
			float a1 = sq(1f - t);
			band(m, p, prev, a0, cur, a1);
			prev = cur;
		}
		submit(g, m);
	}

	/** Круг: центр (cx, cy), радиус rad. */
	public static void drawSmoothCircle(GuiGraphics g, float cx, float cy, float rad, int color) {
		drawSmoothRoundedRect(g, cx - rad, cy - rad, rad * 2f, rad * 2f, rad, color, color);
	}

	/** Круг с градиентом сверху вниз. */
	public static void drawSmoothCircle(GuiGraphics g, float cx, float cy, float rad, int top, int bottom) {
		drawSmoothRoundedRect(g, cx - rad, cy - rad, rad * 2f, rad * 2f, rad, top, bottom);
	}

	/** Кольцо: rad — радиус средней линии, t — толщина. */
	public static void drawSmoothRing(GuiGraphics g, float cx, float cy, float rad, float t, int color) {
		if (rad <= 0f || t <= 0f || alpha(color) == 0) {
			return;
		}
		float outer = rad + t / 2f;
		Mesh m = new Mesh();
		Shape s = new Shape(cx - outer, cy - outer, outer * 2f, outer * 2f, outer);
		stroke(m, s, new Paint(cx - outer, cy - outer, outer * 2f, outer * 2f, color, color, color, color), -t / 2f, t,
				feather());
		submit(g, m);
	}

	/** Линия толщиной t (плоские концы). */
	public static void drawSmoothLine(GuiGraphics g, float x0, float y0, float x1, float y1, float t, int color) {
		Mesh m = new Mesh();
		line(m, x0, y0, x1, y1, t, color, feather());
		submit(g, m);
	}

	/**
	 * Ломаная с круглыми стыками и концами (для галочек, шевронов, крестиков). Стыки рисуются кругами, поэтому при
	 * прозрачном цвете в местах перекрытия цвет чуть гуще — для иконок это не заметно.
	 */
	public static void drawSmoothPolyline(GuiGraphics g, float[] pts, float t, int color) {
		if (pts.length < 4 || alpha(color) == 0) {
			return;
		}
		float f = feather();
		Mesh m = new Mesh();
		for (int i = 0; i + 3 < pts.length; i += 2) {
			line(m, pts[i], pts[i + 1], pts[i + 2], pts[i + 3], t, color, f);
		}
		if (t > 1.2f) {
			for (int i = 0; i + 1 < pts.length; i += 2) {
				float rad = t / 2f;
				Shape s = new Shape(pts[i] - rad, pts[i + 1] - rad, t, t, rad);
				fill(m, s, new Paint(pts[i] - rad, pts[i + 1] - rad, t, t, color, color, color, color), f);
			}
		}
		submit(g, m);
	}

	/** Выпуклый многоугольник (точки в порядке обхода), заливка одним цветом с мягким краем. */
	public static void drawSmoothPolygon(GuiGraphics g, float[] pts, int color) {
		int n = pts.length / 2;
		if (n < 3 || alpha(color) == 0) {
			return;
		}
		float cx = 0f;
		float cy = 0f;
		for (int i = 0; i < n; i++) {
			cx += pts[2 * i];
			cy += pts[2 * i + 1];
		}
		cx /= n;
		cy /= n;
		float f = feather();
		// порядок обхода определяем по знаку площади, чтобы не зависеть от входных данных
		float area = 0f;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			area += pts[2 * i] * pts[2 * j + 1] - pts[2 * j] * pts[2 * i + 1];
		}
		float sign = area >= 0 ? 1f : -1f;
		Mesh m = new Mesh();
		float[] inner = new float[pts.length];
		float[] outer = new float[pts.length];
		for (int i = 0; i < n; i++) {
			float dx = pts[2 * i] - cx;
			float dy = pts[2 * i + 1] - cy;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			float ux = len < 1e-4f ? 0f : dx / len;
			float uy = len < 1e-4f ? 0f : dy / len;
			inner[2 * i] = pts[2 * i] - ux * f / 2f;
			inner[2 * i + 1] = pts[2 * i + 1] - uy * f / 2f;
			outer[2 * i] = pts[2 * i] + ux * f / 2f;
			outer[2 * i + 1] = pts[2 * i + 1] + uy * f / 2f;
		}
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			if (sign > 0) {
				m.tri(cx, cy, color, inner[2 * i], inner[2 * i + 1], color, inner[2 * j], inner[2 * j + 1], color);
			} else {
				m.tri(cx, cy, color, inner[2 * j], inner[2 * j + 1], color, inner[2 * i], inner[2 * i + 1], color);
			}
			int c0 = color;
			int c1 = withAlphaMul(color, 0f);
			m.tri(inner[2 * i], inner[2 * i + 1], c0, outer[2 * i], outer[2 * i + 1], c1, outer[2 * j], outer[2 * j + 1], c1);
			m.tri(inner[2 * i], inner[2 * i + 1], c0, outer[2 * j], outer[2 * j + 1], c1, inner[2 * j], inner[2 * j + 1], c0);
		}
		submit(g, m);
	}

	/**
	 * «Остров» с уступом: широкая верхняя часть (topW x topH) и более узкая нижняя (botW x botH), центр по cx.
	 * Углы верха и низа — радиус r, выпуклые углы низа верхней части — rc, вогнутые скругления стыка — rf
	 * (радиусы сами ужимаются, если не помещаются). Если уступа нет, рисуется обычный скруглённый прямоугольник.
	 * Контур строится один раз, заливка полосами по строкам, край и обводка сглажены полосой в 1 физический пиксель.
	 */
	public static void drawSmoothIsland(GuiGraphics g, float cx, float y, float topW, float topH, float botW,
			float botH, float r, float rc, float rf, int top, int bottom, int borderColor, float border,
			int glowColor, float glowSpread) {
		float d = (topW - botW) / 2f;
		float total = topH + botH;
		if (d < 0.5f || topH < r + 0.5f) {
			float w = Math.max(topW, botW);
			float rr = Math.min(r, Math.min(w, total) / 2f);
			if (glowSpread > 0f) {
				drawSmoothGlow(g, cx - w / 2f, y, w, total, rr, glowColor, glowSpread);
			}
			drawSmoothRoundedRect(g, cx - w / 2f, y, w, total, rr, top, bottom, borderColor, border);
			return;
		}
		float f = feather();
		float left = cx - topW / 2f;
		float bl = cx - botW / 2f;
		float yu = y + topH;
		float y1 = y + total;
		r = Math.min(r, Math.min(topH, botH) / 2f + 1f);
		rc = Math.max(0f, Math.min(rc, topH - r));
		rf = Math.max(0f, Math.min(rf, botH - r));
		float need = rc + rf;
		if (need > d) {
			float k = d / need;
			rc *= k;
			rf *= k;
		}
		java.util.ArrayList<float[]> lp = new java.util.ArrayList<>();
		addPt(lp, cx, y);
		addArc(lp, left + r, y + r, r, -90f, -180f);
		addPt(lp, left, yu - rc);
		addArc(lp, left + rc, yu - rc, rc, 180f, 90f);
		addArc(lp, bl - rf, yu + rf, rf, -90f, 0f);
		addPt(lp, bl, y1 - r);
		addArc(lp, bl + r, y1 - r, r, 180f, 90f);
		addPt(lp, cx, y1);
		int m = lp.size();
		int n = 2 * m - 2;
		float[] loop = new float[2 * n];
		for (int i = 0; i < m; i++) {
			loop[2 * i] = lp.get(i)[0];
			loop[2 * i + 1] = lp.get(i)[1];
		}
		for (int i = m - 2; i >= 1; i--) {
			int k = n - i;
			loop[2 * k] = 2f * cx - lp.get(i)[0];
			loop[2 * k + 1] = lp.get(i)[1];
		}
		float[] nx = new float[n];
		float[] ny = new float[n];
		for (int i = 0; i < n; i++) {
			int a = (i + n - 1) % n;
			int b = (i + 1) % n;
			float ex1 = loop[2 * i] - loop[2 * a];
			float ey1 = loop[2 * i + 1] - loop[2 * a + 1];
			float ex2 = loop[2 * b] - loop[2 * i];
			float ey2 = loop[2 * b + 1] - loop[2 * i + 1];
			float l1 = (float) Math.sqrt(ex1 * ex1 + ey1 * ey1);
			float l2 = (float) Math.sqrt(ex2 * ex2 + ey2 * ey2);
			float ax = 0f;
			float ay = 0f;
			if (l1 > 1e-4f) {
				ax += -ey1 / l1;
				ay += ex1 / l1;
			}
			if (l2 > 1e-4f) {
				ax += -ey2 / l2;
				ay += ex2 / l2;
			}
			float l = (float) Math.sqrt(ax * ax + ay * ay);
			nx[i] = l < 1e-4f ? 0f : ax / l;
			ny[i] = l < 1e-4f ? 0f : ay / l;
		}
		Mesh mesh = new Mesh();
		if (glowSpread > 0f && alpha(glowColor) > 0) {
			Paint gp = new Paint(left, y, topW, total, glowColor, glowColor, glowColor, glowColor);
			float[] prev = offsetLoop(loop, nx, ny, 0f);
			int steps = 6;
			for (int k = 1; k <= steps; k++) {
				float t = k / (float) steps;
				float[] cur = offsetLoop(loop, nx, ny, glowSpread * t);
				band(mesh, gp, prev, sq(1f - (k - 1) / (float) steps), cur, sq(1f - t));
				prev = cur;
			}
		}
		Paint p = new Paint(left, y, topW, total, top, top, bottom, bottom);
		float[] in = offsetLoop(loop, nx, ny, -f / 2f);
		float[] out = offsetLoop(loop, nx, ny, f / 2f);
		for (int i = 0; i < m - 1; i++) {
			int li = i;
			int lj = i + 1;
			int ri = (i == 0) ? 0 : n - i;
			int rj = (i + 1 == m - 1) ? m - 1 : n - (i + 1);
			float[] q = {in[2 * li], in[2 * li + 1], in[2 * ri], in[2 * ri + 1], in[2 * rj], in[2 * rj + 1],
					in[2 * lj], in[2 * lj + 1]};
			int c0 = p.at(q[0], q[1], 1f);
			int c1 = p.at(q[2], q[3], 1f);
			int c2 = p.at(q[4], q[5], 1f);
			int c3 = p.at(q[6], q[7], 1f);
			mesh.tri(q[0], q[1], c0, q[2], q[3], c1, q[4], q[5], c2);
			mesh.tri(q[0], q[1], c0, q[4], q[5], c2, q[6], q[7], c3);
		}
		band(mesh, p, in, 1f, out, 0f);
		if (border > 0f && alpha(borderColor) > 0) {
			Paint bp = new Paint(left, y, topW, total, borderColor, borderColor, borderColor, borderColor);
			float eff = Math.max(border, f);
			float ao = Math.min(1f, border / f);
			float core = (eff - f) / 2f;
			float center = -border / 2f;
			float[] a = offsetLoop(loop, nx, ny, center - core - f);
			float[] b = offsetLoop(loop, nx, ny, center - core);
			float[] c = offsetLoop(loop, nx, ny, center + core);
			float[] e = offsetLoop(loop, nx, ny, center + core + f);
			band(mesh, bp, a, 0f, b, ao);
			if (core > 0.001f) {
				band(mesh, bp, b, ao, c, ao);
			}
			band(mesh, bp, c, ao, e, 0f);
		}
		submit(g, mesh);
	}

	private static void addPt(java.util.List<float[]> l, float x, float y) {
		if (!l.isEmpty()) {
			float[] last = l.get(l.size() - 1);
			if (Math.abs(last[0] - x) < 1e-3f && Math.abs(last[1] - y) < 1e-3f) {
				return;
			}
		}
		l.add(new float[] {x, y});
	}

	/** Дуга окружности (углы в градусах, ось y вниз), точки включая оба конца. */
	private static void addArc(java.util.List<float[]> l, float cx, float cy, float rad, float a0, float a1) {
		if (rad < 1e-3f) {
			addPt(l, cx, cy);
			return;
		}
		int seg = Math.max(2, segments(rad) * 2);
		for (int i = 0; i <= seg; i++) {
			double a = Math.toRadians(a0 + (a1 - a0) * i / seg);
			addPt(l, cx + (float) (rad * Math.cos(a)), cy + (float) (rad * Math.sin(a)));
		}
	}

	private static float[] offsetLoop(float[] loop, float[] nx, float[] ny, float off) {
		float[] o = new float[loop.length];
		for (int i = 0; i < nx.length; i++) {
			o[2 * i] = loop[2 * i] + nx[i] * off;
			o[2 * i + 1] = loop[2 * i + 1] + ny[i] * off;
		}
		return o;
	}

	// ---------- внутренности: построение ----------

	/** Размер одного физического пикселя экрана в единицах GUI. */
	private static float feather() {
		double scale = 2.0;
		try {
			scale = Minecraft.getInstance().getWindow().getGuiScale();
		} catch (Throwable ignored) {
			// окно ещё не создано
		}
		return (float) (1.0 / Math.max(1.0, scale));
	}

	private static float guiScale() {
		return 1f / feather();
	}

	/** Число сегментов дуги: растёт с радиусом в физических пикселях, 2..16. */
	static int segments(float r) {
		double px = r * guiScale();
		int n = (int) Math.ceil(Math.sqrt(Math.max(0.0, px)) * 2.4);
		return Math.max(2, Math.min(16, n));
	}

	private static float sq(float v) {
		return v * v;
	}

	private static int alpha(int argb) {
		return argb >>> 24;
	}

	/** Умножает альфу на k без нижнего порога (в отличие от RoundedRender.scaleAlpha). */
	public static int mulAlpha(int argb, float k) {
		return withAlphaMul(argb, k);
	}

	static int withAlphaMul(int argb, float k) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, k)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Заливка: веер до контура −f/2 + полоса до +f/2 с падением альфы. */
	private static void fill(Mesh m, Shape s, Paint p, float f) {
		float inset = Math.min(f / 2f, Math.min(s.w, s.h) / 2f - 0.01f);
		float[] in = s.ring(-inset);
		float[] out = s.ring(f / 2f);
		float cx = s.x + s.w / 2f;
		float cy = s.y + s.h / 2f;
		int pts = in.length / 2;
		int cc = p.at(cx, cy, 1f);
		for (int i = 0; i < pts; i++) {
			int j = (i + 1) % pts;
			m.tri(cx, cy, cc,
					in[2 * i], in[2 * i + 1], p.at(in[2 * i], in[2 * i + 1], 1f),
					in[2 * j], in[2 * j + 1], p.at(in[2 * j], in[2 * j + 1], 1f));
		}
		band(m, p, in, 1f, out, 0f);
	}

	/**
	 * Обводка вокруг контура: center — смещение средней линии (отрицательное = внутрь), t — толщина.
	 * Толщина меньше пикселя превращается в волосок с уменьшенной альфой.
	 */
	private static void stroke(Mesh m, Shape s, Paint p, float center, float t, float f) {
		float eff = Math.max(t, f);
		float ao = Math.min(1f, t / f);
		float core = (eff - f) / 2f;
		float[] a = s.ring(center - core - f);
		float[] b = s.ring(center - core);
		float[] c = s.ring(center + core);
		float[] d = s.ring(center + core + f);
		band(m, p, a, 0f, b, ao);
		if (core > 0.001f) {
			band(m, p, b, ao, c, ao);
		}
		band(m, p, c, ao, d, 0f);
	}

	/** Полоса между двумя контурами одинаковой топологии, альфа в вершинах a0 (внутренний) и a1 (внешний). */
	private static void band(Mesh m, Paint p, float[] in, float a0, float[] out, float a1) {
		int pts = in.length / 2;
		for (int i = 0; i < pts; i++) {
			int j = (i + 1) % pts;
			int ci = p.at(in[2 * i], in[2 * i + 1], a0);
			int cj = p.at(in[2 * j], in[2 * j + 1], a0);
			int oi = p.at(out[2 * i], out[2 * i + 1], a1);
			int oj = p.at(out[2 * j], out[2 * j + 1], a1);
			m.tri(in[2 * i], in[2 * i + 1], ci, out[2 * i], out[2 * i + 1], oi, out[2 * j], out[2 * j + 1], oj);
			m.tri(in[2 * i], in[2 * i + 1], ci, out[2 * j], out[2 * j + 1], oj, in[2 * j], in[2 * j + 1], cj);
		}
	}

	private static void line(Mesh m, float x0, float y0, float x1, float y1, float t, int color, float f) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-4f || alpha(color) == 0) {
			return;
		}
		float ux = dx / len;
		float uy = dy / len;
		float nx = -uy;
		float ny = ux;
		float eff = Math.max(t, f);
		float ao = Math.min(1f, t / f);
		float core = (eff - f) / 2f;
		float[] off = {-(core + f), -core, core, core + f};
		float[] al = {0f, ao, ao, 0f};
		for (int k = 0; k < 3; k++) {
			float ax = x0 + nx * off[k];
			float ay = y0 + ny * off[k];
			float bx = x1 + nx * off[k];
			float by = y1 + ny * off[k];
			float cx = x1 + nx * off[k + 1];
			float cy = y1 + ny * off[k + 1];
			float ex = x0 + nx * off[k + 1];
			float ey = y0 + ny * off[k + 1];
			int ca = withAlphaMul(color, al[k]);
			int cb = withAlphaMul(color, al[k + 1]);
			m.tri(ax, ay, ca, bx, by, ca, cx, cy, cb);
			m.tri(ax, ay, ca, cx, cy, cb, ex, ey, cb);
		}
	}

	// ---------- отправка в GUI-рендер ----------

	private static void submit(GuiGraphics g, Mesh m) {
		if (m.n == 0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		int x0 = (int) Math.floor(m.minX) - 1;
		int y0 = (int) Math.floor(m.minY) - 1;
		int x1 = (int) Math.ceil(m.maxX) + 1;
		int y1 = (int) Math.ceil(m.maxY) + 1;
		ScreenRectangle bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}
		g.guiRenderState.submitGuiElement(new MeshState(m, pose, bounds, scissor));
	}

	/** Элемент GUI-рендера: готовая сетка треугольников. */
	private static final class MeshState implements GuiElementRenderState {
		private final Mesh mesh;
		private final Matrix3x2f pose;
		private final ScreenRectangle bounds;
		private final ScreenRectangle scissor;

		MeshState(Mesh mesh, Matrix3x2f pose, ScreenRectangle bounds, ScreenRectangle scissor) {
			this.mesh = mesh;
			this.pose = pose;
			this.bounds = bounds;
			this.scissor = scissor;
		}

		@Override
		public void buildVertices(VertexConsumer vertices) {
			for (int i = 0; i < mesh.n; i++) {
				vertices.addVertexWith2DPose(pose, mesh.xs[i], mesh.ys[i]).setColor(mesh.cs[i]);
			}
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}

		@Override
		public RenderPipeline pipeline() {
			return Pipelines.SMOOTH;
		}

		@Override
		public ScreenRectangle bounds() {
			return bounds;
		}

		@Override
		public ScreenRectangle scissorArea() {
			return scissor;
		}
	}

	// ---------- вспомогательные структуры ----------

	/** Растущий буфер вершин (по 3 на треугольник). */
	static final class Mesh {
		float[] xs = new float[256];
		float[] ys = new float[256];
		int[] cs = new int[256];
		int n;
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		void tri(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
			if (n + 3 > xs.length) {
				int cap = xs.length * 2;
				xs = java.util.Arrays.copyOf(xs, cap);
				ys = java.util.Arrays.copyOf(ys, cap);
				cs = java.util.Arrays.copyOf(cs, cap);
			}
			v(x0, y0, c0);
			v(x1, y1, c1);
			v(x2, y2, c2);
		}

		private void v(float x, float y, int c) {
			xs[n] = x;
			ys[n] = y;
			cs[n] = c;
			n++;
			if (x < minX) {
				minX = x;
			}
			if (x > maxX) {
				maxX = x;
			}
			if (y < minY) {
				minY = y;
			}
			if (y > maxY) {
				maxY = y;
			}
		}
	}

	/** Цвет в точке: билинейная интерполяция цветов углов прямоугольника (x, y, w, h) + множитель альфы. */
	static final class Paint {
		final float x;
		final float y;
		final float w;
		final float h;
		final int tl;
		final int tr;
		final int bl;
		final int br;
		private final boolean vertical;

		Paint(float x, float y, float w, float h, int tl, int tr, int bl, int br) {
			this.x = x;
			this.y = y;
			this.w = Math.max(1e-3f, w);
			this.h = Math.max(1e-3f, h);
			this.tl = tl;
			this.tr = tr;
			this.bl = bl;
			this.br = br;
			this.vertical = tl == tr && bl == br;
		}

		int at(float px, float py, float alphaMul) {
			float v = Math.max(0f, Math.min(1f, (py - y) / h));
			float a;
			float r;
			float gg;
			float b;
			if (vertical) {
				a = lerp(tl >>> 24, bl >>> 24, v);
				r = lerp((tl >> 16) & 255, (bl >> 16) & 255, v);
				gg = lerp((tl >> 8) & 255, (bl >> 8) & 255, v);
				b = lerp(tl & 255, bl & 255, v);
			} else {
				float u = Math.max(0f, Math.min(1f, (px - x) / w));
				a = bil(tl >>> 24, tr >>> 24, bl >>> 24, br >>> 24, u, v);
				r = bil((tl >> 16) & 255, (tr >> 16) & 255, (bl >> 16) & 255, (br >> 16) & 255, u, v);
				gg = bil((tl >> 8) & 255, (tr >> 8) & 255, (bl >> 8) & 255, (br >> 8) & 255, u, v);
				b = bil(tl & 255, tr & 255, bl & 255, br & 255, u, v);
			}
			int ia = Math.round(a * alphaMul);
			return (clamp(ia) << 24) | (clamp(Math.round(r)) << 16) | (clamp(Math.round(gg)) << 8) | clamp(Math.round(b));
		}

		private static float lerp(float a, float b, float t) {
			return a + (b - a) * t;
		}

		private static float bil(float tl, float tr, float bl, float br, float u, float v) {
			return lerp(lerp(tl, tr, u), lerp(bl, br, u), v);
		}

		private static int clamp(int v) {
			return v < 0 ? 0 : Math.min(255, v);
		}
	}

	/** Скруглённый прямоугольник и его контуры со смещением. */
	static final class Shape {
		final float x;
		final float y;
		final float w;
		final float h;
		final float r;
		final int n;

		Shape(float x, float y, float w, float h, float r) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
			this.n = this.r < 0.05f ? 1 : segments(this.r);
		}

		/**
		 * Контур, смещённый на off наружу (off &lt; 0 — внутрь). Обход против часовой стрелки на экране: правая сторона
		 * вверх, верх влево, левая вниз, низ вправо. Всегда ровно 4·(n+1) точек.
		 */
		float[] ring(float off) {
			float rad = r + off;
			float cl;
			float ct;
			float cr;
			float cb;
			if (rad >= 0f) {
				cl = x + r;
				ct = y + r;
				cr = x + w - r;
				cb = y + h - r;
			} else {
				rad = 0f;
				cl = x - off;
				ct = y - off;
				cr = x + w + off;
				cb = y + h + off;
			}
			float[] p = new float[8 * (n + 1)];
			int k = 0;
			for (int corner = 0; corner < 4; corner++) {
				for (int j = 0; j <= n; j++) {
					double phi = Math.PI / 2.0 * j / n;
					float c = (float) Math.cos(phi);
					float s = (float) Math.sin(phi);
					switch (corner) {
						case 0 -> { // правый верхний: из «вправо» в «вверх»
							p[k++] = cr + rad * c;
							p[k++] = ct - rad * s;
						}
						case 1 -> { // левый верхний: из «вверх» в «влево»
							p[k++] = cl - rad * s;
							p[k++] = ct - rad * c;
						}
						case 2 -> { // левый нижний: из «влево» в «вниз»
							p[k++] = cl - rad * c;
							p[k++] = cb + rad * s;
						}
						default -> { // правый нижний: из «вниз» в «вправо»
							p[k++] = cr + rad * s;
							p[k++] = cb + rad * c;
						}
					}
				}
			}
			return p;
		}
	}
}
