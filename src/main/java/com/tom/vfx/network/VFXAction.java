package com.tom.vfx.network;

import java.util.Locale;

/**
 * Action carried by a {@link VFXTriggerPayload}: either start playing an effect or stop all
 * running instances of it.
 */
public enum VFXAction {
	PLAY(0),
	STOP(1);

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
