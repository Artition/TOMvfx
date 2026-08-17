package com.tom.vfx.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

/**
 * A datapack-defined VFX effect loaded from {@code data/<namespace>/vfx/<effect>.json}.
 * The JSON declares the effect kind, the default duration, the default easing curve and
 * a set of parameters (either constant values or start/end animated pairs).
 */
public class VFXDefinition {
	private final Identifier id;
	private final VFXEffectType type;
	private final int defaultDuration;
	private final EasingType defaultEasing;
	private final Map<String, ParamSpec> params;
	private final boolean persistent;
	private final boolean loop;
	private final int fadeTicks;
	private final List<ChildEffect> children;
	private final List<BlockPos> positions;
	private final List<String> entityTargets;
	private final @Nullable Identifier sound;

	/** Safety cap for the entity target list (datapack input, see AGENTS.md). */
	private static final int MAX_ENTITY_TARGETS = 16;

	private VFXDefinition(
		final Identifier id,
		final VFXEffectType type,
		final int defaultDuration,
		final EasingType defaultEasing,
		final Map<String, ParamSpec> params,
		final boolean persistent,
		final boolean loop,
		final int fadeTicks,
		final List<ChildEffect> children,
		final List<BlockPos> positions,
		final List<String> entityTargets,
		final @Nullable Identifier sound
	) {
		this.id = id;
		this.type = type;
		this.defaultDuration = defaultDuration;
		this.defaultEasing = defaultEasing;
		this.params = Collections.unmodifiableMap(params);
		this.persistent = persistent;
		this.loop = loop;
		this.fadeTicks = fadeTicks;
		this.children = List.copyOf(children);
		this.positions = List.copyOf(positions);
		this.entityTargets = List.copyOf(entityTargets);
		this.sound = sound;
	}

	/**
	 * Creates a definition programmatically (used for the built-in effects).
	 */
	public static VFXDefinition create(
		final Identifier id,
		final VFXEffectType type,
		final int defaultDuration,
		final EasingType defaultEasing,
		final Map<String, ParamSpec> params
	) {
		return create(id, type, defaultDuration, defaultEasing, params, false, false, 0, List.of(), List.of(), List.of(), null);
	}

	/**
	 * Creates a definition programmatically with persistence, looping, fade and collection children.
	 */
	public static VFXDefinition create(
		final Identifier id,
		final VFXEffectType type,
		final int defaultDuration,
		final EasingType defaultEasing,
		final Map<String, ParamSpec> params,
		final boolean persistent,
		final boolean loop,
		final int fadeTicks,
		final List<ChildEffect> children
	) {
		return create(id, type, defaultDuration, defaultEasing, params, persistent, loop, fadeTicks, children, List.of(), List.of(), null);
	}

	/**
	 * Creates a definition programmatically with all fields, including entity targets
	 * (UUID strings or player names, used by the {@code entity_tint}/{@code entity_outline}
	 * world overlays).
	 */
	public static VFXDefinition create(
		final Identifier id,
		final VFXEffectType type,
		final int defaultDuration,
		final EasingType defaultEasing,
		final Map<String, ParamSpec> params,
		final boolean persistent,
		final boolean loop,
		final int fadeTicks,
		final List<ChildEffect> children,
		final List<BlockPos> positions,
		final List<String> entityTargets,
		final @Nullable Identifier sound
	) {
		return new VFXDefinition(id, type, defaultDuration, defaultEasing, params, persistent, loop, fadeTicks, children, positions, entityTargets, sound);
	}

	/**
	 * Parses a definition from a datapack JSON object.
	 *
	 * @param id   the effect id (derived from the file name)
	 * @param json the parsed {@code vfx/<name>.json} contents
	 * @return the parsed definition
	 */
	public static VFXDefinition parse(final Identifier id, final JsonObject json) {
		VFXEffectType type = VFXEffectType.fromString(GsonHelper.getAsString(json, "type", ""));
		if (type == null) {
			throw new IllegalArgumentException("Unknown effect type in '" + id + "': " + GsonHelper.getAsString(json, "type", ""));
		}

		int duration = GsonHelper.getAsInt(json, "duration", 40);
		EasingType easing = EasingType.fromString(GsonHelper.getAsString(json, "easing", "LINEAR"));
		boolean persistent = GsonHelper.getAsBoolean(json, "persistent", false);
		boolean loop = GsonHelper.getAsBoolean(json, "loop", false);
		int fadeTicks = GsonHelper.getAsInt(json, "fade_ticks", persistent || loop ? 10 : 0);

		Map<String, ParamSpec> params = new LinkedHashMap<>();
		if (json.has("params")) {
			JsonObject paramsJson = GsonHelper.getAsJsonObject(json, "params");
			for (Map.Entry<String, JsonElement> entry : paramsJson.entrySet()) {
				params.put(entry.getKey(), parseParam(entry.getValue()));
			}
		}

		List<ChildEffect> children = new ArrayList<>();
		if (json.has("effects")) {
			for (JsonElement entry : GsonHelper.getAsJsonArray(json, "effects")) {
				children.add(parseChild(entry));
			}
		}

		List<BlockPos> positions = parsePositions(json, params);

		List<String> entityTargets = new ArrayList<>();
		if (json.has("targets")) {
			for (JsonElement entry : GsonHelper.getAsJsonArray(json, "targets")) {
				String target = GsonHelper.convertToString(entry, "target");
				if (entityTargets.size() >= MAX_ENTITY_TARGETS) {
					throw new IllegalArgumentException("Too many entity targets (max " + MAX_ENTITY_TARGETS + "): " + id);
				}
				entityTargets.add(target);
			}
		}

		Identifier sound = null;
		if (json.has("sound") && !json.get("sound").isJsonNull()) {
			sound = Identifier.parse(GsonHelper.getAsString(json, "sound"));
		}

		return new VFXDefinition(id, type, duration, easing, params, persistent, loop, fadeTicks, children, positions, entityTargets, sound);
	}

	private static List<BlockPos> parsePositions(final JsonObject json, final Map<String, ParamSpec> params) {
		List<BlockPos> positions = new ArrayList<>();
		if (json.has("positions")) {
			for (JsonElement entry : GsonHelper.getAsJsonArray(json, "positions")) {
				JsonArray array = GsonHelper.convertToJsonArray(entry, "position");
				if (array.size() != 3) {
					throw new IllegalArgumentException("Position must be an array of [x, y, z]: " + entry);
				}
				positions.add(new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()));
			}
		}
		if (positions.isEmpty()) {
			// Fallback to legacy single-position params.
			ParamSpec x = params.get("pos_x");
			ParamSpec y = params.get("pos_y");
			ParamSpec z = params.get("pos_z");
			if (x != null && y != null && z != null && !x.animated() && !y.animated() && !z.animated()
				&& x.keyframes().isEmpty() && y.keyframes().isEmpty() && z.keyframes().isEmpty()
				&& x.bound() == null && y.bound() == null && z.bound() == null) {
				positions.add(new BlockPos((int) x.constant(), (int) y.constant(), (int) z.constant()));
			}
		}
		return positions;
	}

	private static ChildEffect parseChild(final JsonElement element) {
		JsonObject object = GsonHelper.convertToJsonObject(element, "effect entry");
		Identifier effectId = Identifier.parse(GsonHelper.getAsString(object, "effect"));
		float delay = GsonHelper.getAsFloat(object, "delay", 0.0F);
		int duration = GsonHelper.getAsInt(object, "duration", 0);
		EasingType easing = object.has("easing") && !object.get("easing").isJsonNull() ? EasingType.fromString(GsonHelper.getAsString(object, "easing")) : null;
		Map<String, Float> overrides = new LinkedHashMap<>();
		if (object.has("params")) {
			JsonObject paramsJson = GsonHelper.getAsJsonObject(object, "params");
			for (Map.Entry<String, JsonElement> entry : paramsJson.entrySet()) {
				JsonElement value = entry.getValue();
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
					overrides.put(entry.getKey(), value.getAsFloat());
				} else {
					throw new IllegalArgumentException("Collection child params must be plain numbers: " + element);
				}
			}
		}
		return new ChildEffect(effectId, delay, duration, overrides, easing);
	}

	/**
	 * One entry of a collection definition: which effect to play, after which delay, with which
	 * duration, constant parameter overrides and easing.
	 *
	 * @param effect  child effect id
	 * @param delay   delay in ticks before the child starts (relative to the collection start)
	 * @param duration child duration in ticks (0 = the child definition default, -1 = persistent)
	 * @param params  constant parameter overrides
	 * @param easing  easing override (null = the child definition default)
	 */
	public record ChildEffect(Identifier effect, float delay, int duration, Map<String, Float> params, EasingType easing) {
	}

	private static ParamSpec parseParam(final JsonElement element) {
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isNumber()) {
				return ParamSpec.constant(primitive.getAsFloat());
			}
			throw new IllegalArgumentException("Parameter must be a number or an object: " + element);
		}

		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("bind")) {
				return ParamSpec.bound(parseBound(object));
			}
			if (object.has("keyframes")) {
				return ParamSpec.keyframed(parseKeyframes(object));
			}
			if (object.has("start") && object.has("end")) {
				float start = GsonHelper.getAsFloat(object, "start");
				float end = GsonHelper.getAsFloat(object, "end");
				return ParamSpec.animated(start, end);
			}
			if (object.has("value")) {
				return ParamSpec.constant(GsonHelper.getAsFloat(object, "value"));
			}
			throw new IllegalArgumentException("Animated parameter must define 'start' and 'end', 'keyframes' or 'value': " + element);
		}

		throw new IllegalArgumentException("Unsupported parameter value: " + element);
	}

	private static List<Keyframe> parseKeyframes(final JsonObject object) {
		JsonArray array = GsonHelper.getAsJsonArray(object, "keyframes");
		List<Keyframe> keyframes = new ArrayList<>();
		for (JsonElement entry : array) {
			JsonObject frame = GsonHelper.convertToJsonObject(entry, "keyframe");
			float time = GsonHelper.getAsFloat(frame, "time");
			float value = GsonHelper.getAsFloat(frame, "value");
			EasingType easing = EasingType.fromString(GsonHelper.getAsString(frame, "easing", "LINEAR"));
			keyframes.add(new Keyframe(time, value, easing));
		}
		return keyframes;
	}

	private static BoundParam parseBound(final JsonObject object) {
		BoundParam.Kind kind = BoundParam.Kind.fromString(GsonHelper.getAsString(object, "bind"));
		double x = 0.0;
		double y = 0.0;
		double z = 0.0;
		if (kind.needsPos()) {
			JsonArray pos = GsonHelper.getAsJsonArray(object, "pos");
			if (pos.size() != 3) {
				throw new IllegalArgumentException("Binding 'pos' must be an array of [x, y, z]: " + object);
			}
			x = pos.get(0).getAsDouble();
			y = pos.get(1).getAsDouble();
			z = pos.get(2).getAsDouble();
		}
		float defaultRange = switch (kind) {
			case LOOK -> 90.0F;
			case SPEED -> 5.0F;
			default -> 16.0F;
		};
		float range = GsonHelper.getAsFloat(object, "range", defaultRange);
		boolean invert = GsonHelper.getAsBoolean(object, "invert", false);
		float scale = GsonHelper.getAsFloat(object, "scale", 1.0F);
		float yaw = GsonHelper.getAsFloat(object, "yaw", 0.0F);
		float pitch = GsonHelper.getAsFloat(object, "pitch", 0.0F);
		return new BoundParam(kind, x, y, z, yaw, pitch, range, invert, scale);
	}

	/**
	 * Builds a playable timeline for an instance of this effect.
	 *
	 * @param durationTicks the effective duration in ticks (payload value, or the definition default)
	 * @param overrides     user-supplied constant parameter overrides (may be empty)
	 * @param easing        the effective easing curve (payload value, or the definition default)
	 */
	public VFXTimeline createTimeline(final float durationTicks, final Map<String, Float> overrides, final EasingType easing) {
		float duration = Math.max(1.0F, durationTicks);
		Map<String, AnimatedValue> values = new LinkedHashMap<>();
		Map<String, BoundParam> bindings = new LinkedHashMap<>();
		for (Map.Entry<String, ParamSpec> entry : this.params.entrySet()) {
			String name = entry.getKey();
			ParamSpec spec = entry.getValue();
			Float override = overrides.get(name);
			if (override != null) {
				values.put(name, AnimatedValue.constant(override));
			} else if (spec.bound() != null) {
				bindings.put(name, spec.bound());
			} else if (!spec.keyframes().isEmpty()) {
				values.put(name, AnimatedValue.fromKeyframes(spec.keyframes().toArray(new Keyframe[0])));
			} else if (spec.animated()) {
				values.put(name, AnimatedValue.between(0.0F, duration, spec.start(), spec.end(), easing));
			} else {
				values.put(name, AnimatedValue.constant(spec.constant()));
			}
		}
		return new VFXTimeline(duration, values, bindings);
	}

	/**
	 * Returns a constant parameter value, or the fallback when the parameter is absent, animated or bound.
	 */
	public float getParam(final String name, final float fallback) {
		ParamSpec spec = this.params.get(name);
		if (spec == null || spec.animated() || !spec.keyframes().isEmpty() || spec.bound() != null) {
			return fallback;
		}
		return spec.constant();
	}

	public Identifier getId() {
		return this.id;
	}

	public VFXEffectType getType() {
		return this.type;
	}

	public int getDefaultDuration() {
		return this.defaultDuration;
	}

	public EasingType getDefaultEasing() {
		return this.defaultEasing;
	}

	public Map<String, ParamSpec> getParams() {
		return this.params;
	}

	/**
	 * True when instances of this definition run forever until stopped.
	 */
	public boolean isPersistent() {
		return this.persistent;
	}

	/**
	 * True when the timeline restarts from the beginning every time it reaches its duration.
	 * Looping effects are implicitly persistent.
	 */
	public boolean isLoop() {
		return this.loop;
	}

	/**
	 * Fade duration in ticks used when a persistent instance starts or is stopped.
	 */
	public int getFadeTicks() {
		return this.fadeTicks;
	}

	/**
	 * Child effects of a collection definition (empty for regular effects).
	 */
	public List<ChildEffect> getChildren() {
		return this.children;
	}

	/**
	 * World positions this effect applies to (for world-space effects such as block highlighting).
	 */
	public List<BlockPos> getPositions() {
		return this.positions;
	}

	/**
	 * Entity targets of this effect (UUID strings or player names), used by the
	 * {@code entity_tint}/{@code entity_outline} world overlays.
	 */
	public List<String> getEntityTargets() {
		return this.entityTargets;
	}

	/**
	 * Optional sound event played on the client when the effect starts.
	 */
	public @Nullable Identifier getSound() {
		return this.sound;
	}

	/**
	 * A single parameter specification: a constant value, an animated start/end pair, a list
	 * of keyframes (in ticks relative to the effect start) or a world binding.
	 *
	 * @param animated  true when the parameter animates between {@link #start()} and {@link #end()}
	 * @param constant  constant value (when not animated)
	 * @param start     start value (when animated)
	 * @param end       end value (when animated)
	 * @param keyframes keyframe list (when keyframed); empty otherwise
	 * @param bound     world binding (when bound); {@code null} otherwise
	 */
	public record ParamSpec(boolean animated, float constant, float start, float end, List<Keyframe> keyframes, BoundParam bound) {
		public static ParamSpec constant(final float value) {
			return new ParamSpec(false, value, 0.0F, 0.0F, List.of(), null);
		}

		public static ParamSpec animated(final float start, final float end) {
			return new ParamSpec(true, 0.0F, start, end, List.of(), null);
		}

		public static ParamSpec keyframed(final List<Keyframe> keyframes) {
			return new ParamSpec(false, 0.0F, 0.0F, 0.0F, List.copyOf(keyframes), null);
		}

		public static ParamSpec bound(final BoundParam binding) {
			return new ParamSpec(false, 0.0F, 0.0F, 0.0F, List.of(), binding);
		}
	}
}
