package com.tom.vfx.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of custom easing curves from datapack {@code data/<namespace>/vfx_curves/<name>.json}
 * files, refreshed on every (server) data reload together with {@link VFXDefinitionManager}.
 * One malformed curve file is logged and skipped, the rest keep loading.
 */
public class VFXCurveManager extends SimplePreparableReloadListener<Map<Identifier, VFXCurve>> {
	private static final Logger LOGGER = LoggerFactory.getLogger("tompfx/vfx-curves");
	private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("vfx_curves");

	private static final VFXCurveManager INSTANCE = new VFXCurveManager();

	private volatile Map<Identifier, VFXCurve> curves = Map.of();

	private VFXCurveManager() {
	}

	public static VFXCurveManager get() {
		return INSTANCE;
	}

	/**
	 * Returns the curve with the given id, or {@code null}. A bare name (no namespace) is looked
	 * up in the {@code minecraft} namespace, matching how plain effect names resolve.
	 */
	public VFXCurve get(final String name) {
		return this.curves.get(Identifier.parse(name));
	}

	public Map<Identifier, VFXCurve> getCurves() {
		return Map.copyOf(this.curves);
	}

	@Override
	protected Map<Identifier, VFXCurve> prepare(final ResourceManager manager, final ProfilerFiller profiler) {
		Map<Identifier, VFXCurve> loaded = new HashMap<>();
		for (Entry<Identifier, Resource> entry : FILE_CONVERTER.listMatchingResources(manager).entrySet()) {
			Identifier fileId = entry.getKey();
			Identifier curveId = FILE_CONVERTER.fileToId(fileId);
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonObject json = StrictJsonParser.parse(reader).getAsJsonObject();
				float[] ts = new float[0];
				float[] vs = new float[0];
				if (json.has("points")) {
					JsonArray points = GsonHelper.getAsJsonArray(json, "points");
					ts = new float[points.size()];
					vs = new float[points.size()];
					for (int i = 0; i < points.size(); i++) {
						JsonArray point = GsonHelper.convertToJsonArray(points.get(i), "point");
						if (point.size() != 2) {
							throw new IllegalArgumentException("Curve point must be [t, v]: " + point);
						}
						ts[i] = point.get(0).getAsFloat();
						vs[i] = point.get(1).getAsFloat();
						if (i > 0 && ts[i] <= ts[i - 1]) {
							throw new IllegalArgumentException("Curve times must be strictly ascending: " + point);
						}
					}
					if (ts.length < 2 || Math.abs(ts[0]) > 1.0e-4F || Math.abs(ts[ts.length - 1] - 1.0F) > 1.0e-4F) {
						throw new IllegalArgumentException("Curve times must start at 0 and end at 1");
					}
				}
				loaded.put(curveId, new VFXCurve(curveId, EasingFunction.curve(curveId.toString(), ts, vs)));
			} catch (JsonParseException | IllegalStateException | IllegalArgumentException | IOException e) {
				LOGGER.error("Couldn't parse VFX curve '{}' from '{}'", curveId, fileId, e);
			}
		}
		return loaded;
	}

	@Override
	protected void apply(final Map<Identifier, VFXCurve> loaded, final ResourceManager manager, final ProfilerFiller profiler) {
		this.curves = Map.copyOf(loaded);
		LOGGER.info("Loaded {} VFX curves from datapacks", loaded.size());
	}
}