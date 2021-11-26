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

import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_ENERGY_ABS;
import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_ENERGY_REL;
import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_POWER;
import static org.openhab.binding.warp.internal.WARPBindingConstants.UNIT_KILO_WATT_HOUR;
import static org.openhab.binding.warp.internal.WARPBindingConstants.UNIT_WATT;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WARPEnergyManagerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Olaf Lüke - Initial contribution
 */
@NonNullByDefault
public class WARPEnergyManagerHandler extends BaseThingHandler implements WARPReaderEnergyMeterListener {

    private final Logger logger = LoggerFactory.getLogger(WARPEnergyManagerHandler.class);
    private @Nullable ScheduledFuture<?> pollingJob;

    private @Nullable WARPConfiguration config;
    private @Nullable WARPReader warpReader = null;
    private @Nullable WARPChargerHandler warpChargerHandler = null;

    public WARPEnergyManagerHandler(Thing thing) {
        super(thing);
    }

    // Generally the Energy Manager is online when the bridge is online
    public void pollBridge() {
        if (getBridge().getStatusInfo().getStatus() == ThingStatus.ONLINE) {
            if (getThing().getStatusInfo().getStatus() != ThingStatus.ONLINE) {
                logger.debug("pollReader: updateStatus(Online)");
                updateStatus(ThingStatus.ONLINE);
            }
        } else if (getBridge().getStatusInfo().getStatus() == ThingStatus.OFFLINE) {
            if (getThing().getStatusInfo().getStatus() != ThingStatus.OFFLINE) {
                logger.debug("pollReader: updateStatus(Offline)");
                updateStatus(ThingStatus.OFFLINE);
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    protected @Nullable WARPChargerHandler getBridgeHandler() {
        Bridge localBridge = getBridge();
        return localBridge != null ? (WARPChargerHandler) localBridge.getHandler() : null;
    }

    @Override
    public void initialize() {
        config = getConfigAs(WARPConfiguration.class);
        updateStatus(ThingStatus.UNKNOWN);

        warpChargerHandler = getBridgeHandler();
        if (warpChargerHandler != null) {
            warpReader = warpChargerHandler.getWarpReader();
            warpReader.addEnergyMeterListener(this);
        }

        pollingJob = scheduler.scheduleWithFixedDelay(this::pollBridge, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        logger.debug("Dispose {}", getThing().getUID());

        final ScheduledFuture<?> pollingJob = this.pollingJob;
        if (pollingJob != null) {
            pollingJob.cancel(true);
            this.pollingJob = null;
        }
    }

    private void warpUpdateState(String channelID, State state) {
        // If we get an update and the thing is offline it means that is has come online
        if (getThing().getStatusInfo().getStatus() != ThingStatus.ONLINE) {
            logger.debug("warpUpdateState: updateStatus(Online)");
            updateStatus(ThingStatus.ONLINE);
        }
        updateState(channelID, state);
    }

    @Override
    public void energyMeterPower(float power) {
        logger.debug("energyMeterPower({})", power);
        warpUpdateState(CHANNEL_POWER, new QuantityType<>(power, UNIT_WATT));
    }

    @Override
    public void energyMeterEnergyRelative(float energy) {
        logger.debug("energyMeterEnergyRelative({})", energy);
        warpUpdateState(CHANNEL_ENERGY_REL, new QuantityType<>(energy, UNIT_KILO_WATT_HOUR));
    }

    @Override
    public void energyMeterEnergyAbsolute(float energy) {
        logger.debug("energyMeterEnergyAbsolute({})", energy);
        warpUpdateState(CHANNEL_ENERGY_ABS, new QuantityType<>(energy, UNIT_KILO_WATT_HOUR));
    }
}
