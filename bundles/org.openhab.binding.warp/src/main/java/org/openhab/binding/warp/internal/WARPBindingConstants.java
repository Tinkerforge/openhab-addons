/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.warp.internal;

import static org.openhab.core.library.unit.MetricPrefix.KILO;
import static org.openhab.core.library.unit.MetricPrefix.MILLI;

import javax.measure.Unit;
import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link WARPBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Olaf Lüke
 */
@NonNullByDefault
public class WARPBindingConstants {

    private static final String BINDING_ID = "warp";

    // Units
    public static final Unit<Power> UNIT_WATT = Units.WATT;
    public static final Unit<Power> UNIT_KILO_WATT = KILO(Units.WATT);
    public static final Unit<Energy> UNIT_KILO_WATT_HOUR = KILO(Units.WATT_HOUR);
    public static final Unit<ElectricCurrent> UNIT_AMPERE = Units.AMPERE;
    public static final Unit<ElectricCurrent> UNIT_MILLI_AMPERE = MILLI(Units.AMPERE);

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_WARP_CHARGER = new ThingTypeUID(BINDING_ID, "warp-charger");
    public static final ThingTypeUID THING_TYPE_WARP_ENERGY_METER = new ThingTypeUID(BINDING_ID, "warp-energy-meter");

    // List of all Channel ids
    public static final String CHANNEL_VEHICLE_STATE = "vehicle-state";
    public static final String CHANNEL_ALLOWED_CHARGING_CURRENT = "allowed-charging-current";
    public static final String CHANNEL_AUTOSTART = "autostart";
    public static final String CHANNEL_START_STOP_CHARGING = "start-stop-charging";
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_ENERGY_REL = "energy-rel";
    public static final String CHANNEL_ENERGY_ABS = "energy-abs";
}
