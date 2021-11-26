package org.openhab.binding.warp.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketFrameListener;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
interface WARPReaderChargerListener {
    void chargerVehicleState(String state);

    void chargerAllowedChargingCurrent(long current);

    void chargerAutostartCharging(boolean autostart);
}

@NonNullByDefault
interface WARPReaderEnergyMeterListener {
    void energyMeterPower(float power);

    void energyMeterEnergyRelative(float energy);

    void energyMeterEnergyAbsolute(float energy);
}

public class WARPReader implements WebSocketListener, WebSocketFrameListener {
    public class EVSEState {
        public long iec61851_state;
        public long vehicle_state;
        public long contactor_state;
        public long contactor_error;
        public long charge_release;
        public long allowed_charging_current;
        public long error_state;
        public long lock_state;
        public long time_since_state_change;
        public long uptime;
    }

    public class EVSEHardwareConfiguration {
        public long jumper_configuration;
        public boolean has_lock_switch;
    }

    public class EVSELowLevelState {
        public long led_state;
        public long cp_pwm_duty_cycle;
        public long[] adc_values;
        public long[] voltages;
        public long[] resistances;
        public boolean[] gpio;
        public long charging_time;
    }

    public class EVSEMaxChargingCurrent {
        public long max_current_configured;
        public long max_current_incoming_cable;
        public long max_current_outgoing_cable;
        public long max_current_managed;
    }

    public class EVSEAutoChargeCurrent {
        public boolean auto_start_charging;
    }

    public class EVSEAutoChargeCurrentUpdate {
        public boolean auto_start_charging;
    }

    public class EVSECurrentLimit {
        public long current;
    }

    public class EVSEEnergyMeterValues {
        public float power;
        public float energy_rel;
        public float energy_abs;
        public boolean[] phases_active;
        public boolean[] phases_connected;
    }

    public class EVSEEnergyMeterState {
        public boolean available;
        public long[] error_count;
    }

    public class EVSEDCFaultCurrentState {
        public long state;
    }

    public class EVSEGPIOConfiguration {
        public long shutdown_input;
        public long input;
        public long output;
    }

    public class EVSEManagedCurrent {
        public long current;
    }

    public class EVSEManaged {
        public boolean managed;
    }

    public class EVSEManagedUpdate {
        public boolean managed;
        public long password;
    }

    public class EVSEButtonConfiguration {
        public long button;
    }

    public class EVSEButtonConfigurationUpdate {
        public long button;
    }

    public class EVSEButtonState {
        public long button_press_time;
        public long button_release_time;
        public boolean button_pressed;
    }

    public class Version {
        public String firmware;
        public String spiffs;
    }

    public class MeterDetailedValues {
        public String topic;
        public float[] payload;
    }

    private final Logger logger = LoggerFactory.getLogger(WARPReader.class);
    private WebSocketClient client;
    private volatile Session session;
    private Future<?> sessionFuture;
    private boolean updateAll = false;
    private WARPConfiguration config;

    private EVSEState[] evseState = new EVSEState[2];
    private EVSEHardwareConfiguration[] evseHardwareConfiguration = new EVSEHardwareConfiguration[2];
    private EVSELowLevelState[] evseLowLevelState = new EVSELowLevelState[2];
    private EVSEMaxChargingCurrent[] evseMaxChargingCurrent = new EVSEMaxChargingCurrent[2];
    private EVSEAutoChargeCurrent[] evseAutoChargeCurrent = new EVSEAutoChargeCurrent[2];
    private EVSEEnergyMeterValues[] evseEnergyMeterValues = new EVSEEnergyMeterValues[2];
    private EVSEEnergyMeterState[] evseEnergyMeterState = new EVSEEnergyMeterState[2];
    private EVSEDCFaultCurrentState[] evseDCFaultCurrentState = new EVSEDCFaultCurrentState[2];
    private EVSEGPIOConfiguration[] evseGPIOConfiguration = new EVSEGPIOConfiguration[2];
    private EVSEManaged[] evseManaged = new EVSEManaged[2];
    private EVSEButtonConfiguration[] evseButtonConfiguration = new EVSEButtonConfiguration[2];
    private EVSEButtonState[] evseButtonState = new EVSEButtonState[2];
    private Version[] version = new Version[2];
    private MeterDetailedValues[] meterDetailedValues = new MeterDetailedValues[2];

    private List<WARPReaderChargerListener> listenersCharger = new ArrayList<WARPReaderChargerListener>();
    private List<WARPReaderEnergyMeterListener> listenersEnergyMeter = new ArrayList<WARPReaderEnergyMeterListener>();

    public WARPReader(WARPConfiguration config) {
        this.config = config;

        for (int i = 0; i < 2; i++) {
            evseState[i] = new EVSEState();
            evseHardwareConfiguration[i] = new EVSEHardwareConfiguration();
            evseLowLevelState[i] = new EVSELowLevelState();
            evseMaxChargingCurrent[i] = new EVSEMaxChargingCurrent();
            evseAutoChargeCurrent[i] = new EVSEAutoChargeCurrent();
            evseEnergyMeterValues[i] = new EVSEEnergyMeterValues();
            evseEnergyMeterState[i] = new EVSEEnergyMeterState();
            evseDCFaultCurrentState[i] = new EVSEDCFaultCurrentState();
            evseGPIOConfiguration[i] = new EVSEGPIOConfiguration();
            evseManaged[i] = new EVSEManaged();
            evseButtonConfiguration[i] = new EVSEButtonConfiguration();
            evseButtonState[i] = new EVSEButtonState();
            version[i] = new Version();
            meterDetailedValues[i] = new MeterDetailedValues();
        }
    }

    public void dispose() {
        closeConnection();
    }

    public void addChargerListener(WARPReaderChargerListener toAdd) {
        listenersCharger.add(toAdd);
    }

    public void addEnergyMeterListener(WARPReaderEnergyMeterListener toAdd) {
        listenersEnergyMeter.add(toAdd);
    }

    public void poll() {
        checkConnection();
    }

    public void updateAllChannels() {
        updateAll = true;
    }

    public boolean isConnected() {
        return (this.session != null) && (this.client != null) && this.session.isOpen();
    }

    @Override
    public void onWebSocketConnect(Session session) {
        logger.debug("WARP onWebSocketConnect('{}')", session);
        this.session = session;

        // On (re-)connect we update all values to make sure we are in a sane state
        updateAll = true;
    }

    @Override
    public void onWebSocketError(Throwable e) {
        logger.debug("WARP error during websocket communication: {}", e.getMessage(), e);
        if (session != null) {
            session.close(StatusCode.SERVER_ERROR, "Failure: " + e.getMessage());
            session = null;
        }
    }

    @Override
    public void onWebSocketText(String msg) {
        // logger.debug("WARP onWebSocketText('{}')", msg);

        // We expect one JSON object per line
        String lines[] = msg.split("\\r?\\n");
        for (String line : lines) {
            if (line == "") {
                continue;
            }
            JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();
            String topic = jsonObject.get("topic").getAsString();
            Gson gson = new Gson();
            JsonElement element = jsonObject.get("payload");
            switch (topic) {
                case "evse/state": {
                    evseState[1] = evseState[0];
                    evseState[0] = gson.fromJson(element, EVSEState.class);
                    // logger.debug("New EVSEState({})", ReflectionToStringBuilder.toString(evseState[0]));
                    break;
                }

                case "evse/hardware_configuration": {
                    evseHardwareConfiguration[1] = evseHardwareConfiguration[0];
                    evseHardwareConfiguration[0] = gson.fromJson(element, EVSEHardwareConfiguration.class);
                    // logger.debug("New EVSEHardwareConfiguration({})",
                    // ReflectionToStringBuilder.toString(evseHardwareConfiguration[0]));
                    break;
                }

                case "evse/low_level_state": {
                    evseLowLevelState[1] = evseLowLevelState[0];
                    evseLowLevelState[0] = gson.fromJson(element, EVSELowLevelState.class);
                    // logger.debug("New EVSELowLevelState({})",
                    // ReflectionToStringBuilder.toString(evseLowLevelState[0]));
                    break;
                }

                case "evse/max_charging_current": {
                    evseMaxChargingCurrent[1] = evseMaxChargingCurrent[0];
                    evseMaxChargingCurrent[0] = gson.fromJson(element, EVSEMaxChargingCurrent.class);
                    // logger.debug("New EVSEMaxChargingCurrent({})",
                    // ReflectionToStringBuilder.toString(evseMaxChargingCurrent[0]));
                    break;
                }

                case "evse/auto_start_charging": {
                    evseAutoChargeCurrent[1] = evseAutoChargeCurrent[0];
                    evseAutoChargeCurrent[0] = gson.fromJson(element, EVSEAutoChargeCurrent.class);
                    // logger.debug("New EVSEAutoChargeCurrent({})",
                    // ReflectionToStringBuilder.toString(evseAutoChargeCurrent[0]));
                    break;
                }

                case "meter/state": // WARP1
                case "evse/energy_meter_values": { // WARP2
                    evseEnergyMeterValues[1] = evseEnergyMeterValues[0];
                    evseEnergyMeterValues[0] = gson.fromJson(element, EVSEEnergyMeterValues.class);
                    // logger.debug("New EVSEEnergyMeterValues({})",
                    // ReflectionToStringBuilder.toString(evseEnergyMeterValues[0]));
                    break;
                }

                case "evse/energy_meter_state": {
                    evseEnergyMeterState[1] = evseEnergyMeterState[0];
                    evseEnergyMeterState[0] = gson.fromJson(element, EVSEEnergyMeterState.class);
                    // logger.debug("New EVSEEnergyMeterState({})",
                    // ReflectionToStringBuilder.toString(evseEnergyMeterState[0]));
                    break;
                }

                case "evse/dc_fault_current_state": {
                    evseDCFaultCurrentState[1] = evseDCFaultCurrentState[0];
                    evseDCFaultCurrentState[0] = gson.fromJson(element, EVSEDCFaultCurrentState.class);
                    // logger.debug("New EVSEDCFaultCurrentState({})",
                    // ReflectionToStringBuilder.toString(evseDCFaultCurrentState[0]));
                    break;
                }

                case "evse/gpio_configuration": {
                    evseGPIOConfiguration[1] = evseGPIOConfiguration[0];
                    evseGPIOConfiguration[0] = gson.fromJson(element, EVSEGPIOConfiguration.class);
                    // logger.debug("New EVSEGPIOConfiguration({})",
                    // ReflectionToStringBuilder.toString(evseGPIOConfiguration[0]));
                    break;
                }

                case "evse/button_configuration": {
                    evseButtonConfiguration[1] = evseButtonConfiguration[0];
                    evseButtonConfiguration[0] = gson.fromJson(element, EVSEButtonConfiguration.class);
                    // logger.debug("New EVSEButtonConfiguration({})",
                    // ReflectionToStringBuilder.toString(evseButtonConfiguration[0]));
                    break;
                }

                case "evse/managed": {
                    evseManaged[1] = evseManaged[0];
                    evseManaged[0] = gson.fromJson(element, EVSEManaged.class);
                    // logger.debug("New EVSEManaged({})", ReflectionToStringBuilder.toString(evseManaged[0]));
                    break;
                }

                case "evse/button_state": {
                    evseButtonState[1] = evseButtonState[0];
                    evseButtonState[0] = gson.fromJson(element, EVSEButtonState.class);
                    // logger.debug("New EVSEButtonState({})", ReflectionToStringBuilder.toString(evseButtonState[0]));
                    break;
                }

                case "version": {
                    version[1] = version[0];
                    version[0] = gson.fromJson(element, Version.class);
                    // logger.debug("New Version({})", ReflectionToStringBuilder.toString(version[0]));
                    break;
                }

                case "meter/detailed_values": {
                    meterDetailedValues[1] = meterDetailedValues[0];
                    meterDetailedValues[0] = gson.fromJson(line, MeterDetailedValues.class);
                    // logger.debug("New MeterDetailedValues({})",
                    // ReflectionToStringBuilder.toString(meterDetailedValues[0]));
                    break;
                }

                case "keep-alive":
                case "modules":
                case "evse/control_pilot_configuration":
                case "wifi/state":
                case "wifi/sta_config":
                case "wifi/ap_config":
                case "ethernet/config":
                case "ethernet/state":
                case "mqtt/config":
                case "mqtt/state":
                case "authentication/config":
                case "charge_manager/config":
                case "charge_manager/state":
                case "charge_manager/available_current":
                case "nfc/seen_tags":
                case "nfc/config":
                case "evse/user_calibration":
                case "meter/error_counters": {
                    // Known, but unhandled
                    // logger.debug("Unhandled Topic({})", topic);
                    break;
                }

                default: {
                    logger.debug("Unknown Topic({})", topic);
                    break;
                }
            }
        }

        checkListeners();
    }

    @Override
    public void onWebSocketBinary(byte[] arr, int pos, int len) {
        // We don't expect binary data
        logger.debug("WARP unexpected onWebSocketBinary({}, {}, '{}')", pos, len, Arrays.toString(arr));
    }

    @Override
    public void onWebSocketClose(int code, String reason) {
        logger.debug("WARP onWebSocketClose({}, '{}')", code, reason);
    }

    @Override
    public void onWebSocketFrame(Frame frame) {
        // logger.debug("WARP onWebSocketFrame({}, {})", pingCount, frame);
    }

    private void checkListeners() {
        if (updateAll || (evseState[0].vehicle_state != evseState[1].vehicle_state)) {
            String state = "Unknown";
            if (evseState[0].vehicle_state == 0) {
                state = "Not Connected";
            } else if (evseState[0].vehicle_state == 1) {
                state = "Connected";
            } else if (evseState[0].vehicle_state == 2) {
                state = "Charging";
            } else if (evseState[0].vehicle_state == 3) {
                state = "Error";
            }

            logger.debug("vehicle_state from {} to {} (listeners: {})", evseState[1].vehicle_state,
                    evseState[0].vehicle_state, listenersCharger.size());
            for (WARPReaderChargerListener wrl : listenersCharger) {
                wrl.chargerVehicleState(state);
            }

            evseState[1].vehicle_state = evseState[0].vehicle_state;
        }

        if (updateAll || (evseState[0].allowed_charging_current != evseState[1].allowed_charging_current)) {
            logger.debug("allowed_charging_current from {} to {} (listeners: {})",
                    evseState[1].allowed_charging_current, evseState[0].allowed_charging_current,
                    listenersCharger.size());
            for (WARPReaderChargerListener wrl : listenersCharger) {
                wrl.chargerAllowedChargingCurrent(evseState[0].allowed_charging_current);
            }
            evseState[1].allowed_charging_current = evseState[0].allowed_charging_current;
        }

        if (updateAll
                || (evseAutoChargeCurrent[0].auto_start_charging != evseAutoChargeCurrent[1].auto_start_charging)) {
            logger.debug("auto_start_charging from {} to {} (listeners: {})",
                    evseAutoChargeCurrent[1].auto_start_charging, evseAutoChargeCurrent[0].auto_start_charging,
                    listenersCharger.size());
            for (WARPReaderChargerListener wrl : listenersCharger) {
                wrl.chargerAutostartCharging(evseAutoChargeCurrent[0].auto_start_charging);
            }
            evseAutoChargeCurrent[1].auto_start_charging = evseAutoChargeCurrent[0].auto_start_charging;
        }

        if (updateAll || (evseEnergyMeterValues[0].power != evseEnergyMeterValues[1].power)) {
            for (WARPReaderEnergyMeterListener wrl : listenersEnergyMeter) {
                wrl.energyMeterPower(evseEnergyMeterValues[0].power);
            }

            evseEnergyMeterValues[1].power = evseEnergyMeterValues[0].power;
        }

        if (updateAll || (evseEnergyMeterValues[0].energy_rel != evseEnergyMeterValues[1].energy_rel)) {
            for (WARPReaderEnergyMeterListener wrl : listenersEnergyMeter) {
                wrl.energyMeterEnergyRelative(evseEnergyMeterValues[0].energy_rel);
            }

            evseEnergyMeterValues[1].energy_rel = evseEnergyMeterValues[0].energy_rel;
        }

        if (updateAll || (evseEnergyMeterValues[0].energy_abs != evseEnergyMeterValues[1].energy_abs)) {
            for (WARPReaderEnergyMeterListener wrl : listenersEnergyMeter) {
                wrl.energyMeterEnergyAbsolute(evseEnergyMeterValues[0].energy_abs);
            }

            evseEnergyMeterValues[1].energy_abs = evseEnergyMeterValues[0].energy_abs;
        }

        updateAll = false;
    }

    private synchronized void openConnection() {
        if (config == null || config.hostname == "") {
            return;
        }

        closeConnection();
        try {
            client = new WebSocketClient();

            String wsUrl = "ws://" + config.hostname + "/ws";
            logger.debug("WARP connecting to: {}", wsUrl);
            client.start();
            sessionFuture = client.connect(this, new URI(wsUrl));
        } catch (Exception e) {
            onWebSocketError(e);
        }
    }

    private synchronized void closeConnection() {
        if (session != null) {
            try {
                session.close(StatusCode.NORMAL, "Binding shutdown");
            } catch (Exception e) {
                logger.debug("WARP error while closing websocket communication: {} ({})", e.getClass().getName(),
                        e.getMessage());
            }
            session = null;
        }
        if (sessionFuture != null && !sessionFuture.isDone()) {
            sessionFuture.cancel(true);
        }
        if (client != null) {
            try {
                client.stop();
                client.destroy();
            } catch (Exception e) {
                logger.debug("WARP error while closing websocket communication: {} ({})", e.getClass().getName(),
                        e.getMessage());
            }
            client = null;
        }
    }

    private void checkConnection() {
        if (session == null || client == null || !session.isOpen()) {
            openConnection(); // Try to reconnect
        }
    }
}
