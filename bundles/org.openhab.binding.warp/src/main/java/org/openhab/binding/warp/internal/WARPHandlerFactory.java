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

import static org.openhab.binding.warp.internal.WARPBindingConstants.THING_TYPE_WARP_CHARGER;
import static org.openhab.binding.warp.internal.WARPBindingConstants.THING_TYPE_WARP_ENERGY_METER;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WARPHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Olaf Lüke
 */
@NonNullByDefault
@Component(configurationPid = "binding.warp", service = ThingHandlerFactory.class)
public class WARPHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(WARPHandlerFactory.class);
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_WARP_CHARGER,
            THING_TYPE_WARP_ENERGY_METER);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_WARP_CHARGER.equals(thingTypeUID)) {
            logger.debug("New Thing: {}", thingTypeUID);
            return new WARPChargerHandler((Bridge) thing);
        } else if (THING_TYPE_WARP_ENERGY_METER.equals(thingTypeUID)) {
            logger.debug("New Thing: {}", thingTypeUID);
            return new WARPEnergyManagerHandler(thing);
        }

        return null;
    }
}
