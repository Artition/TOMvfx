package dev.vfxweaver.network;

import java.util.Locale;

/**
 * Action carried by a {@link VFXTriggerPayload}: start playing an effect, stop all running
 * instances of it, or live-adjust a parameter/keyframe of a running instance.
 */
public enum VFXAction {
	PLAY(0),
	STOP(1),
	SET_PARAM(2),
	KEYFRAME(3);

	private final byte id;

	VFXAction(final int id) {
		this.id = (byte) id;
	}

	public byte getId() {
		return this.id;
	}

	public static VFXAction fromId(final byte id) {
		for (VFXAction action : values()) {
			if (action.id == id) {
				return action;
			}
		}
		return PLAY;
	}

	public static VFXAction fromString(final String name) {
		if (name == null || name.isBlank()) {
			return PLAY;
		}
		try {
			return valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return PLAY;
		}
	}
}
