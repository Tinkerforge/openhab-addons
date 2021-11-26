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

import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_ALLOWED_CHARGING_CURRENT;
import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_AUTOSTART;
import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_START_STOP_CHARGING;
import static org.openhab.binding.warp.internal.WARPBindingConstants.CHANNEL_VEHICLE_STATE;
import static org.openhab.binding.warp.internal.WARPBindingConstants.UNIT_MILLI_AMPERE;

import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WARPChargerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Olaf Lüke - Initial contribution
 */
@NonNullByDefault
public class WARPChargerHandler extends BaseBridgeHandler implements WARPReaderChargerListener {

    private final Logger logger = LoggerFactory.getLogger(WARPChargerHandler.class);
    private @Nullable ScheduledFuture<?> pollingJob;

    private @Nullable WARPConfiguration config;
    private @Nullable WARPReader warpReader = null;
    private @Nullable WARPWriter warpWriter = null;

    public WARPChargerHandler(Bridge bridge) {
        super(bridge);
    }

    public @Nullable WARPReader getWarpReader() {
        return warpReader;
    }

    public void pollReader() {
        warpReader.poll();
        if (warpReader.isConnected()) {
            if (getThing().getStatusInfo().getStatus() != ThingStatus.ONLINE) {
                logger.debug("pollReader: updateStatus(Online)");
                updateStatus(ThingStatus.ONLINE);
            }
        } else {
            if (getThing().getStatusInfo().getStatus() != ThingStatus.OFFLINE) {
                logger.debug("pollReader: updateStatus(Offline)");
                updateStatus(ThingStatus.OFFLINE);
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channelID = channelUID.getIdWithoutGroup();
        logger.debug("ChannelUID {}, ChannelID {}, Command {}, CommandInstance {}", channelUID, channelID, command,
                command.getClass().getName());
        switch (channelID) {
            case CHANNEL_ALLOWED_CHARGING_CURRENT:
                if (command instanceof DecimalType) {
                    warpWriter.writeCurrent(((DecimalType) command).longValue());
                    logger.debug("Set CHANNEL_ALLOWED_CHARGING_CURRENT {}", command);
                }
                break;

            case CHANNEL_AUTOSTART:
                if (command instanceof OnOffType) {
                    warpWriter.writeAutostart(((OnOffType) command) == OnOffType.ON);
                    logger.debug("Set CHANNEL_AUTOSTART {}", command);
                }
                break;

            case CHANNEL_START_STOP_CHARGING:
                if (command instanceof StringType) {
                    switch (((StringType) command).toString()) {
                        case "START":
                            warpWriter.writeStartCharging();
                            logger.debug("Set CHANNEL_START_STOP_CHARGING {}", command);
                            break;
                        case "STOP":
                            warpWriter.writeStopCharging();
                            logger.debug("Set CHANNEL_START_STOP_CHARGING {}", command);
                            break;
                        default:
                            logger.warn("Unknown CHANNEL_START_STOP_CHARGING command '{}'", command.toString());
                    }
                }
                break;
        }
        if (command instanceof RefreshType) {
            warpReader.updateAllChannels();
            logger.debug("Refresh: {}", channelUID.getAsString());
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(WARPConfiguration.class);
        logger.debug("Hostname {}", config.hostname);

        warpReader = new WARPReader(config);
        warpWriter = new WARPWriter(config);

        updateStatus(ThingStatus.UNKNOWN);

        logger.debug("pollReader: start");
        pollingJob = scheduler.scheduleWithFixedDelay(this::pollReader, 0, 1, TimeUnit.SECONDS);

        warpReader.addChargerListener(this);
    }

    @Override
    public void dispose() {
        logger.debug("Dispose {}", getThing().getUID());

        final ScheduledFuture<?> pollingJob = this.pollingJob;
        if (pollingJob != null) {
            pollingJob.cancel(true);
            this.pollingJob = null;
        }

        if (warpReader != null) {
            warpReader.dispose();
            warpReader = null;
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
    public void chargerVehicleState(String state) {
        logger.debug("chargerVehicleState({})", state);
        warpUpdateState(CHANNEL_VEHICLE_STATE, new StringType(state));
    }

    @Override
    public void chargerAllowedChargingCurrent(long current) {
        logger.debug("chargerAllowedChargingCurrent({})", current);
        warpUpdateState(CHANNEL_ALLOWED_CHARGING_CURRENT,
                new QuantityType<>(new BigDecimal(current), UNIT_MILLI_AMPERE));
    }

    @Override
    public void chargerAutostartCharging(boolean autostart) {
        logger.debug("chargerAutostartCharging({})", autostart);
        warpUpdateState(CHANNEL_AUTOSTART, OnOffType.from(autostart));
    }
}
