package org.openhab.binding.warp.internal;

import static org.eclipse.jetty.http.HttpHeader.ACCEPT;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class WARPWriter {
    private class EVSECurrent {
        public long current;
    }

    private class EVSEAutostart {
        public boolean auto_start_charging;
    }

    private EVSECurrent evseCurrent = new EVSECurrent();
    private EVSEAutostart evseAutostart = new EVSEAutostart();

    private Gson gson = new Gson();

    private final Logger logger = LoggerFactory.getLogger(WARPWriter.class);
    private WARPConfiguration config;
    private final HttpClient httpClient;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(1);
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";

    public WARPWriter(WARPConfiguration config) {
        this.config = config;
        this.httpClient = new HttpClient();
        try {
            this.httpClient.start();
        } catch (Exception e) {
            logger.debug("WARPWriter Exception: ", e.toString());
        }
    }

    public void dispose() {
        try {
            this.httpClient.stop();
        } catch (Exception e) {
            logger.debug("dispose Exception: ", e.toString());
        }
    }

    public void writeStartCharging() {
        putJson("/evse/start_charging", "");
    }

    public void writeStopCharging() {
        putJson("/evse/stop_charging", "");
    }

    public void writeAutostart(boolean autostart) {
        evseAutostart.auto_start_charging = autostart;
        putJson("/evse/auto_start_charging_update", gson.toJson(evseAutostart));
    }

    public void writeCurrent(long current) {
        evseCurrent.current = current;
        putJson("/evse/current_limit", gson.toJson(evseCurrent));
    }

    private void putJson(String url, String json) {
        String fullUrl = "http://" + config.hostname + url;
        logger.debug("Putting '{}' to '{}'", json, fullUrl);
        String response;
        try {
            response = httpClient.newRequest(fullUrl) //
                    .method(HttpMethod.PUT) //
                    .header(ACCEPT, APPLICATION_JSON) //
                    .content(new StringContentProvider(json), APPLICATION_JSON) //
                    .timeout(REQUEST_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS) //
                    .send() //
                    .getContentAsString();
            logger.debug("Response: {}", response);
        } catch (InterruptedException e) {
            logger.debug("putContent InterruptedException: ", e.toString());
        } catch (TimeoutException e) {
            logger.debug("putContent TimeoutException: ", e.toString());
        } catch (ExecutionException e) {
            logger.debug("putContent ExecutionException: ", e.toString());
        }
    }
}
