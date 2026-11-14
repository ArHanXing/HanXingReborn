/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 TechReborn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package techreborn.client.gui.space;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain drawing/input helper embedded by a native RebornCore screen, not a
 * registered widget or screen. Coordinates passed to setBounds and input events
 * must use the same coordinate space as the current DrawContext matrices.
 *
 * All state is ephemeral CLIENT PREVIEW state: selection, camera and survey
 * animation have no effect on inventories, recipes or server progression.
 */
public final class SpaceMapWidget {

	private static final double TWO_PI = Math.PI * 2;
	private final List<SpaceBody> bodies;
	private final Map<String, SpaceBody> byId = new LinkedHashMap<>();
	private final long createdAt = System.nanoTime();
	private long previousFrame = createdAt;
	private long scanStartedAt = createdAt;
	private String system;
	@Nullable
	private SpaceBody selected;
	private int x;
	private int y;
	private int width;
	private int height;
	private double cameraX;
	private double cameraY;
	private double zoom = 1;
	private boolean following = true;
	private boolean dragging;
	private boolean surveyed;
	private double clipScale = 1;
	private String taskBody = "";
	private String taskKind = "";
	private String taskState = "idle";
	private double taskProgress;
	private String taskEvent = "";
	private String eventBody = "";
	private double eventAge = 100;

	/** Authoritative presentation only; never predicts a completion or grants progress. */
	public void setTaskVisuals(String body, String kind, String state, double progress, String event, String eventBody, double eventAge) {
		this.taskBody = body; this.taskKind = kind; this.taskState = state;
		this.taskProgress = Math.max(0, Math.min(1, progress));
		this.taskEvent = event; this.eventBody = eventBody; this.eventAge = eventAge;
	}

	/** DrawContext scissor rectangles in 1.21.1 are not matrix transformed. */
	public void setClipScale(double clipScale) {
		this.clipScale = clipScale;
	}

	public SpaceMapWidget(List<SpaceBody> bodies) {
		this.bodies = List.copyOf(bodies);
		for (SpaceBody body : bodies) {
			if (byId.putIfAbsent(body.id(), body) != null) {
				throw new IllegalArgumentException("Duplicate client preview body: " + body.id());
			}
		}
		SpaceBody initial = byId.get("sol:moon");
		if (initial == null && !bodies.isEmpty()) {
			initial = bodies.getFirst();
		}
		system = initial == null ? "sol" : initial.system();
		if (initial != null) {
			select(initial);
			Point point = positions(0).get(initial.id());
			cameraX = point.x();
			cameraY = point.y();
		}
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
	}

	public void setSystem(String system) {
		if (this.system.equals(system)) {
			return;
		}
		SpaceBody first = bodies.stream().filter(body -> body.system().equals(system))
				.filter(body -> !body.type().equals("star")).findFirst()
				.orElseGet(() -> bodies.stream().filter(body -> body.system().equals(system)).findFirst().orElse(null));
		if (first == null) {
			return;
		}
		this.system = system;
		zoom = 1;
		cameraX = 0;
		cameraY = 0;
		select(first);
	}

	public String system() {
		return system;
	}

	@Nullable
	public SpaceBody selected() {
		return selected;
	}

	/** Select a catalogue body, switch its system page and smoothly follow it. */
	public void select(SpaceBody body) {
		SpaceBody canonical = byId.get(body.id());
		if (canonical == null) {
			return;
		}
		if (!system.equals(canonical.system())) {
			zoom = 1;
			cameraX = 0;
			cameraY = 0;
		}
		system = canonical.system();
		selected = canonical;
		following = true;
		dragging = false;
		scanStartedAt = System.nanoTime();
	}

	/** Visual preview flag only; deliberately disconnected from team progress. */
	public void setSurveyed(boolean surveyed) {
		if (this.surveyed != surveyed) {
			scanStartedAt = System.nanoTime();
		}
		this.surveyed = surveyed;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		if (width <= 0 || height <= 0) {
			return;
		}
		long now = System.nanoTime();
		double seconds = (now - createdAt) / 1_000_000_000.0;
		double delta = Math.min(.1, Math.max(0, (now - previousFrame) / 1_000_000_000.0));
		previousFrame = now;
		Map<String, Point> positions = positions(seconds);
		if (following && selected != null) {
			Point target = positions.get(selected.id());
			if (target != null) {
				double easing = 1 - Math.exp(-delta * 9);
				cameraX += (target.x() - cameraX) * easing;
				cameraY += (target.y() - cameraY) * easing;
			}
		}

		context.enableScissor((int) Math.floor(x * clipScale), (int) Math.floor(y * clipScale),
				(int) Math.ceil((x + width) * clipScale), (int) Math.ceil((y + height) * clipScale));
		try {
			drawSpace(context, seconds);
			double scale = scale();
			for (SpaceBody body : bodies) {
				if (!body.system().equals(system) || body.parent() == null) {
					continue;
				}
				Point parent = positions.get(body.parent());
				if (parent != null) {
					Point screen = screen(parent, scale);
					drawRing(context, screen.x(), screen.y(), orbitRadius(body) * scale, 1,
							body == selected ? 0x805EBDD7 : 0x35456A87, seconds * .01);
				}
			}

			// Deliberately frame-local: resource reloads cannot leave stale sprites.
			Map<Block, Sprite> sprites = new HashMap<>();
			SpaceBody hovered = hitBody(mouseX, mouseY, positions);
			for (SpaceBody body : bodies) {
				if (!body.system().equals(system)) {
					continue;
				}
				Point point = screen(positions.get(body.id()), scale);
				if (body.type().equals("asteroid_belt")) {
					drawBelt(context, body, positions, scale, seconds, sprites);
				}
				if (!onScreen(point, 32)) {
					continue;
				}
				int size = markerSize(body);
				if (body.type().equals("black_hole")) {
					drawBlackHole(context, point, size, seconds);
				} else {
					if (body.type().equals("star")) {
						drawRing(context, point.x(), point.y(), size * .8, 1, 0x50FFDA84, seconds * .08);
						drawRing(context, point.x(), point.y(), size, 1, 0x20FFB84D, -seconds * .04);
					}
					drawTop(context, body, point, size, sprites);
				}
				if (body == selected || body == hovered) {
					int color = body == selected ? 0xFFD6F8FF : 0xFF7DCBDF;
					drawBrackets(context, point, size + 6, color);
					drawLabel(context, body.name(), point, size, color);
				}
			}

			drawTask(context, positions, scale, seconds);
			drawSurvey(context, positions, scale, seconds, now);
			MinecraftClient client = MinecraftClient.getInstance();
			context.drawTextWithShadow(client.textRenderer, Text.translatable("gui.techreborn.space.map.preview"), x + 7, y + height - 13, 0xFF6F98AF);
		} finally {
			context.disableScissor();
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY) || button != 0) {
			return false;
		}
		SpaceBody hit = hitBody(mouseX, mouseY, positions(elapsedSeconds()));
		if (hit != null) {
			select(hit);
		} else {
			dragging = true;
			following = false;
		}
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (!dragging || button != 0) {
			return false;
		}
		if (contains(mouseX, mouseY)) {
			cameraX -= deltaX / scale();
			cameraY -= deltaY / scale();
		}
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button != 0 || !dragging) {
			return false;
		}
		dragging = false;
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (!contains(mouseX, mouseY) || verticalAmount == 0 || !Double.isFinite(verticalAmount)) {
			return false;
		}
		double oldScale = scale();
		double offsetX = mouseX - (x + width / 2.0);
		double offsetY = mouseY - (y + height / 2.0);
		double anchoredX = cameraX + offsetX / oldScale;
		double anchoredY = cameraY + offsetY / oldScale;
		zoom = Math.max(.15, Math.min(8, zoom * Math.pow(1.18, verticalAmount)));
		cameraX = anchoredX - offsetX / scale();
		cameraY = anchoredY - offsetY / scale();
		following = false;
		return true;
	}

	private void drawSpace(DrawContext context, double seconds) {
		context.fill(x, y, x + width, y + height, 0xFF050A16);
		for (int band = 0; band < 12; band++) {
			int bandY = y + band * height / 12;
			context.fill(x, bandY, x + width, y + (band + 1) * height / 12,
					0x080A2540 + ((11 - band) << 24));
		}
		double parallax = scale() * .025;
		for (int i = 0; i < 180; i++) {
			long hash = mix(i * 7919L + system.hashCode());
			double starX = Math.floorMod(hash, 100003) / 100003.0 * width - cameraX * parallax;
			double starY = Math.floorMod(hash >>> 18, 100019) / 100019.0 * height - cameraY * parallax;
			int px = x + (int) positiveModulo(starX, width);
			int py = y + (int) positiveModulo(starY, height);
			int brightness = (int) (100 + 65 * (1 + Math.sin(seconds * .45 + i)));
			context.fill(px, py, px + 1, py + 1, (brightness << 24) | 0xA8CAE2);
			if (i % 37 == 0) {
				context.fill(px - 1, py, px + 2, py + 1, 0x5087BFE2);
				context.fill(px, py - 1, px + 1, py + 2, 0x5087BFE2);
			}
		}
	}

	private void drawBelt(DrawContext context, SpaceBody body, Map<String, Point> positions, double scale,
			double seconds, Map<Block, Sprite> sprites) {
		Point parent = positions.get(body.parent());
		if (parent == null) {
			return;
		}
		double radius = orbitRadius(body);
		for (int i = 0; i < 72; i++) {
			long hash = mix(i * 733L + body.id().hashCode());
			double angle = i * TWO_PI / 72 + Math.toRadians(body.phaseDegrees()) + seconds * .006;
			double spread = (Math.floorMod(hash, 100) / 100.0 - .5) * 9;
			Point point = screen(new Point(parent.x() + Math.cos(angle) * (radius + spread),
					parent.y() + Math.sin(angle) * (radius + spread)), scale);
			if (onScreen(point, 8)) {
				drawTop(context, body, point, Math.max(2, (int) (4 * Math.sqrt(zoom) * body.markerScale())), sprites);
			}
		}
	}

	private void drawTop(DrawContext context, SpaceBody body, Point point, int size, Map<Block, Sprite> sprites) {
		Sprite sprite = sprites.computeIfAbsent(body.marker(), SpaceMapWidget::topSprite);
		int left = (int) Math.round(point.x()) - size / 2;
		int top = (int) Math.round(point.y()) - size / 2;
		if (sprite == null) {
			context.fill(left, top, left + size, top + size, 0xFF687A89);
		} else {
			// Yarn 1.21.1 DrawContext: x, y, z, width, height, sprite, RGBA.
			context.drawSprite(left, top, 0, size, size, sprite, 1, 1, 1, surveyed ? 1 : .6F);
		}
	}

	@Nullable
	private static Sprite topSprite(Block block) {
		BlockState state = block.getDefaultState();
		BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
		List<BakedQuad> quads = model.getQuads(state, Direction.UP, Random.create(42));
		for (BakedQuad quad : quads) {
			if (quad.getFace() == Direction.UP) {
				return quad.getSprite();
			}
		}
		// Some models put their top faces in the non-culling quad list.
		for (BakedQuad quad : model.getQuads(state, null, Random.create(42))) {
			if (quad.getFace() == Direction.UP) {
				return quad.getSprite();
			}
		}
		return null;
	}

	private void drawBlackHole(DrawContext context, Point point, int size, double seconds) {
		drawRing(context, point.x(), point.y(), size * 1.3, .34, 0xA88570DC, seconds * .05);
		drawRing(context, point.x(), point.y(), size * 1.1, .37, 0xDDEDB48E, -seconds * .05);
		int half = size / 2;
		context.fill((int) point.x() - half, (int) point.y() - half,
				(int) point.x() + half, (int) point.y() + half, 0xFF010207);
		drawRing(context, point.x(), point.y(), half, 1, 0xFFB995DC, seconds * .02);
		drawRing(context, point.x(), point.y(), size * 1.3, .34, 0x60FFD7AE, seconds * .11);
	}

	private void drawSurvey(DrawContext context, Map<String, Point> positions, double scale, double seconds, long now) {
		if (!surveyed) {
			// Soft procedural haze, not a disclosure/security mechanism.
			for (int row = 0; row < height; row += 8) {
				int alpha = 16 + (int) (7 * (1 + Math.sin(row * .06 + seconds * .25)));
				context.fill(x, y + row, x + width, y + Math.min(row + 8, height), (alpha << 24) | 0x0A1832);
			}
		}
		// Scanning and completion pulses are driven by authoritative task data
		// in drawTask, not by simply clicking a body in this viewer.
	}

	private void drawTask(DrawContext context, Map<String, Point> positions, double scale, double seconds) {
		SpaceBody body = byId.get(taskBody);
		if (body != null && body.system().equals(system) && !taskState.equals("idle")) {
			Point target = screen(positions.get(body.id()), scale);
			int color = taskState.equals("running") ? 0xFF68E4F2 : 0xFFE4B85E;
			drawBrackets(context, target, markerSize(body) + 12, color);
			SpaceBody earth = byId.get("sol:earth");
			if (earth == null) {
				earth = bodies.stream().filter(candidate -> candidate.id().endsWith(":earth")).findFirst().orElse(null);
			}
			if (earth != null && earth.system().equals(body.system())) {
				// Compute the transfer in orbital-map coordinates, before camera and
				// zoom. Both endpoint tangents follow their parent's prograde orbit.
				Point departure = positions.get(earth.id());
				if (departure != null) {
					Point earthParent = positions.getOrDefault(earth.parent(), departure);
					Point arrival = positions.get(body.id());
					Point targetParent = positions.getOrDefault(body.parent(), arrival);
					SpaceTransferOrbit transfer = SpaceTransferOrbit.between(orbitVec(departure), orbitVec(earthParent),
							orbitVec(arrival), orbitVec(targetParent));
					drawTransfer(context, transfer, scale, color);
					Point origin = screen(departure, scale);
					drawRing(context, origin.x(), origin.y(), markerSize(earth) * .7 + 3, .75, 0x9078C9F4, seconds * .3);
				}
			} else {
				// Other system pages have independent origins. Do not pretend their
				// star or a viewport corner is Earth; show the off-page origin explicitly.
				context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
						Text.translatable(earth == null ? "gui.techreborn.space.transfer.no_earth" : "gui.techreborn.space.transfer.from_earth"),
						x + 7, y + height - 25, color);
			}
			if (taskState.equals("running")) {
				drawRing(context, target.x(), target.y(), markerSize(body) + 5 + Math.sin(seconds * 3) * 2, .5, 0x8078D8F4, seconds);
				if (taskKind.equals("survey")) { drawRing(context, target.x(), target.y(), 12 + seconds % 2 * 20, 1, 0x6078E9D1, seconds * .4); }
			}
		}
		SpaceBody eventTarget = byId.get(eventBody);
		if (eventTarget != null && eventTarget.system().equals(system) && eventAge >= 0 && eventAge < 4 && !taskEvent.isEmpty()) {
			Point target = screen(positions.get(eventBody), scale);
			int alpha = (int) (210 * (1 - eventAge / 4));
			int color = (alpha << 24) | (taskEvent.equals("complete") ? 0x71F0A5 : taskEvent.equals("refund") ? 0xE9BA68 : 0x75DFFF);
			drawRing(context, target.x(), target.y(), 8 + eventAge * 22, 1, color, seconds * .2);
			context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
					Text.translatable("gui.techreborn.space.event." + taskEvent), x + 7, y + 5, color);
		}
	}

	private static SpaceTransferOrbit.Vec orbitVec(Point point) {
		return new SpaceTransferOrbit.Vec(point.x(), point.y());
	}

	private void drawTransfer(DrawContext context, SpaceTransferOrbit orbit, double scale, int color) {
		// Fixed budget and per-segment clipping remain stable even when Earth is
		// offscreen. Nothing is anchored to viewport edges or the current focus.
		Point previous = screen(new Point(orbit.departure().x(), orbit.departure().y()), scale);
		for (int i = 1; i <= 128; i++) {
			double t = i / 128D;
			var sample = orbit.point(t);
			Point next = screen(new Point(sample.x(), sample.y()), scale);
			drawTransferSegment(context, previous, next, t <= taskProgress ? color : 0x605D8B9A);
			previous = next;
		}
		var sample = orbit.point(taskProgress);
		Point ship = screen(new Point(sample.x(), sample.y()), scale);
		if (onScreen(ship, 0)) {
			var tangent = orbit.direction(taskProgress);
			double length = tangent.length();
			double dx = length > 1E-9 ? tangent.x() / length : 1;
			double dy = length > 1E-9 ? tangent.y() / length : 0;
			drawTransferSegment(context, new Point(ship.x() - dx * 4, ship.y() - dy * 4),
					new Point(ship.x() + dx * 4, ship.y() + dy * 4), color);
			context.fill((int) ship.x() - 1, (int) ship.y() - 1, (int) ship.x() + 2, (int) ship.y() + 2, color);
		}
	}

	private void drawTransferSegment(DrawContext context, Point from, Point to, int color) {
		// Liang-Barsky clips before rasterization: extreme zoom cannot create
		// loops proportional to an offscreen transfer's full pixel length.
		double dx = to.x() - from.x(), dy = to.y() - from.y();
		double low = 0, high = 1;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {from.x() - x, x + width - 1 - from.x(), from.y() - y, y + height - 1 - from.y()};
		for (int i = 0; i < 4; i++) {
			if (Math.abs(p[i]) < 1E-9) { if (q[i] < 0) { return; } }
			else if (p[i] < 0) { low = Math.max(low, q[i] / p[i]); }
			else { high = Math.min(high, q[i] / p[i]); }
			if (low > high) { return; }
		}
		double sx = from.x() + dx * low, sy = from.y() + dy * low;
		double ex = from.x() + dx * high, ey = from.y() + dy * high;
		int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(ex - sx), Math.abs(ey - sy))));
		for (int i = 0; i <= steps; i++) {
			int px = (int) Math.round(sx + (ex - sx) * i / steps);
			int py = (int) Math.round(sy + (ey - sy) * i / steps);
			context.fill(px, py, px + 1, py + 1, color);
		}
	}

	private void drawLabel(DrawContext context, Text text, Point point, int size, int color) {
		MinecraftClient client = MinecraftClient.getInstance();
		int textWidth = client.textRenderer.getWidth(text);
		int left = Math.max(x + 4, Math.min(x + width - textWidth - 4, (int) point.x() - textWidth / 2));
		int top = Math.max(y + 3, Math.min(y + height - 26, (int) point.y() + size / 2 + 7));
		context.fill(left - 3, top - 2, left + textWidth + 3, top + 10, 0xB0071020);
		context.drawTextWithShadow(client.textRenderer, text, left, top, color);
	}

	private void drawBrackets(DrawContext context, Point point, int size, int color) {
		int left = (int) point.x() - size / 2;
		int top = (int) point.y() - size / 2;
		for (int dx : new int[]{0, size}) {
			for (int dy : new int[]{0, size}) {
				int px = left + dx;
				int py = top + dy;
				context.fill(px - (dx == 0 ? 0 : 4), py, px + (dx == 0 ? 4 : 1), py + 1, color);
				context.fill(px, py - (dy == 0 ? 0 : 4), px + 1, py + (dy == 0 ? 4 : 1), color);
			}
		}
	}

	private void drawRing(DrawContext context, double cx, double cy, double radius, double flatten, int color, double phase) {
		if (radius < 1 || cx + radius < x || cx - radius > x + width
				|| cy + radius * flatten < y || cy - radius * flatten > y + height) {
			return;
		}
		int steps = Math.max(32, Math.min(720, (int) (radius * 5)));
		for (int i = 0; i < steps; i++) {
			double angle = i * TWO_PI / steps + phase;
			int px = (int) Math.round(cx + Math.cos(angle) * radius);
			int py = (int) Math.round(cy + Math.sin(angle) * radius * flatten);
			if (contains(px, py)) {
				context.fill(px, py, px + 1, py + 1, color);
			}
		}
	}

	@Nullable
	private SpaceBody hitBody(double mouseX, double mouseY, Map<String, Point> positions) {
		if (!contains(mouseX, mouseY)) {
			return null;
		}
		SpaceBody hit = null;
		double nearest = Double.MAX_VALUE;
		double scale = scale();
		for (SpaceBody body : bodies) {
			if (!body.system().equals(system)) {
				continue;
			}
			Point point = screen(positions.get(body.id()), scale);
			double distance = Math.hypot(mouseX - point.x(), mouseY - point.y());
			double hitRadius = Math.max(7, markerSize(body) / 2.0 + 3);
			if (onScreen(point, 0) && distance <= hitRadius && distance < nearest) {
				nearest = distance;
				hit = body;
			}
		}
		if (hit != null) {
			return hit;
		}
		// Belt particles and the entire visible band select the same belt entry.
		for (SpaceBody body : bodies) {
			if (!body.system().equals(system) || !body.type().equals("asteroid_belt")) {
				continue;
			}
			Point parent = positions.get(body.parent());
			if (parent != null) {
				Point center = screen(parent, scale);
				double distance = Math.abs(Math.hypot(mouseX - center.x(), mouseY - center.y()) - orbitRadius(body) * scale);
				if (distance <= Math.max(5, 7 * scale) && distance < nearest) {
					nearest = distance;
					hit = body;
				}
			}
		}
		return hit;
	}

	private Map<String, Point> positions(double seconds) {
		Map<String, Point> result = new HashMap<>();
		for (SpaceBody body : bodies) {
			if (body.system().equals(system)) {
				position(body, seconds, result, new HashSet<>());
			}
		}
		return result;
	}

	private Point position(SpaceBody body, double seconds, Map<String, Point> result, Set<String> visiting) {
		Point existing = result.get(body.id());
		if (existing != null) {
			return existing;
		}
		if (!visiting.add(body.id())) {
			throw new IllegalArgumentException("Cyclic client preview orbit: " + body.id());
		}
		SpaceBody parentBody = body.parent() == null ? null : byId.get(body.parent());
		Point point;
		if (parentBody == null) {
			point = new Point(0, 0);
		} else {
			Point parent = position(parentBody, seconds, result, visiting);
			double angle = Math.toRadians(body.phaseDegrees()) + seconds * .012 / (1 + Math.log1p(body.orbitRadiusAu()));
			double radius = orbitRadius(body);
			point = new Point(parent.x() + Math.cos(angle) * radius, parent.y() + Math.sin(angle) * radius);
		}
		visiting.remove(body.id());
		result.put(body.id(), point);
		return point;
	}

	/** A single monotonic AU mapping is used for every orbit, including moons. */
	private static double orbitRadius(SpaceBody body) {
		return body.parent() == null ? 0 : 24 + 45 * Math.log1p(body.orbitRadiusAu() * 18);
	}

	private double scale() {
		double extent = 80;
		for (SpaceBody body : bodies) {
			if (body.system().equals(system)) {
				double chain = 0;
				SpaceBody current = body;
				Set<String> visited = new HashSet<>();
				while (current != null && visited.add(current.id())) {
					chain += orbitRadius(current);
					current = current.parent() == null ? null : byId.get(current.parent());
				}
				extent = Math.max(extent, chain + 24);
			}
		}
		// A whole-system fit packed the outer orbits into less than half the map
		// height and made adjacent markers overlap. Start four times closer;
		// pan/follow reaches the outer bodies and scrolling still gives an overview.
		return Math.max(.05, Math.min(width, height) * .43 / extent) * zoom * 4;
	}

	private int markerSize(SpaceBody body) {
		return Math.max(5, Math.min(42, (int) Math.round(12 * body.markerScale() * Math.sqrt(zoom))));
	}

	private Point screen(Point point, double scale) {
		return new Point(x + width / 2.0 + (point.x() - cameraX) * scale,
				y + height / 2.0 + (point.y() - cameraY) * scale);
	}

	private boolean contains(double px, double py) {
		return width > 0 && height > 0 && px >= x && px < x + width && py >= y && py < y + height;
	}

	private boolean onScreen(Point point, double margin) {
		return point.x() >= x - margin && point.x() < x + width + margin
				&& point.y() >= y - margin && point.y() < y + height + margin;
	}

	private double elapsedSeconds() {
		return (System.nanoTime() - createdAt) / 1_000_000_000.0;
	}

	private static double positiveModulo(double value, double divisor) {
		return (value % divisor + divisor) % divisor;
	}

	private static long mix(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private record Point(double x, double y) {
	}
}
