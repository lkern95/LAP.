using Toybox.Activity;
using Toybox.Application.Properties;
using Toybox.Communications;
using Toybox.Graphics;
using Toybox.Lang;
using Toybox.Math;
using Toybox.System;
using Toybox.Time;
using Toybox.WatchUi;

class LapLiveDataField extends WatchUi.DataField {
    private const PING_TEST_MODE = false;
    private const LAYOUT_PREVIEW_MODE = false;
    private const TRANSFER_TIMEOUT_SECONDS = 12;
    private var sequence = 0;
    private var transferPending = false;
    private var transferType = "v1";
    private var transferStartedAt = 0;
    private var transferTimeoutReported = false;
    private var pingSent = false;
    private var transferAttempts = 0;
    private var transferSuccesses = 0;
    private var transferErrors = 0;
    private var timerMillis = 0;
    private var distanceMeters = -1.0;
    private var speedMetersPerSecond = -1.0;
    private var paceSeconds = -1;
    private var heartRate = -1;
    private var paused = true;
    private var backgroundBitmap;
    private var targetDistanceMeters = -1.0;
    private var progressRingFraction = 0.0;
    private var targetAcknowledgementPending = false;
    private var targetTrainingId = "";
    private var targetDurationSeconds = 0;
    private var targetConsumed = false;
    private var wasRecording = false;

    function initialize() {
        DataField.initialize();
        backgroundBitmap = WatchUi.loadResource(Rez.Drawables.WatchBackground);
        targetTrainingId = Properties.getValue("lapTrainingId") != null ? Properties.getValue("lapTrainingId") : "";
        targetDurationSeconds = Properties.getValue("lapTargetSeconds") != null ? Properties.getValue("lapTargetSeconds") : 0;
        targetConsumed = Properties.getValue("lapTargetConsumed") == true;
        wasRecording = Properties.getValue("lapWasRecording") == true;
        Communications.registerForPhoneAppMessages(method(:handlePhoneAppMessage));
    }

    function compute(info) {
        checkTransferTimeout();
        if (info.timerTime != null) {
            timerMillis = info.timerTime;
        }
        updateTargetActivityState(info.timerState);
        distanceMeters = -1.0;
        if (info.elapsedDistance != null && info.elapsedDistance >= 0) {
            distanceMeters = info.elapsedDistance;
        }
        targetDistanceMeters = configuredTargetDistanceMeters();
        var activeTargetSeconds = currentTargetDurationSeconds();
        if (activeTargetSeconds <= 0 && targetDistanceMeters <= 0 && distanceMeters >= 0 && info.distanceToDestination != null && info.distanceToDestination > 0) {
            targetDistanceMeters = distanceMeters + info.distanceToDestination;
        }
        if (info.currentSpeed != null && info.currentSpeed > 0) {
            speedMetersPerSecond = info.currentSpeed;
            paceSeconds = (1000.0 / info.currentSpeed).toNumber();
        } else {
            speedMetersPerSecond = -1.0;
            paceSeconds = -1;
        }
        heartRate = info.currentHeartRate != null && info.currentHeartRate >= 30 ? info.currentHeartRate : -1;
        paused = info.timerState != Activity.TIMER_STATE_ON;
        if (!paused && activeTargetSeconds > 0) {
            progressRingFraction = (timerMillis / (activeTargetSeconds * 1000.0)).toFloat();
            progressRingFraction = progressRingFraction < 0 ? 0.0 : (progressRingFraction > 1 ? 1.0 : progressRingFraction);
        } else if (!paused && distanceMeters >= 0 && targetDistanceMeters > 0) {
            progressRingFraction = (distanceMeters / targetDistanceMeters).toFloat();
            progressRingFraction = progressRingFraction < 0 ? 0.0 : (progressRingFraction > 1 ? 1.0 : progressRingFraction);
        }

        if (info.timerState != null && info.timerState != Activity.TIMER_STATE_OFF) {
            if (targetAcknowledgementPending) {
                transmitTargetAcknowledgement();
                return;
            }
            transmitCurrentSample();
        }
    }

    function handlePhoneAppMessage(message as Toybox.Communications.PhoneAppMessage) as Void {
        var data = message.data as Toybox.Lang.Array<Toybox.Lang.Object or Null>;
        if (data == null || data.size() != 4 || data[0] != "LAP_TARGET" || data[1] != 1) {
            return;
        }
        var trainingId = data[2] as Toybox.Lang.String;
        var durationSeconds = data[3] as Toybox.Lang.Number;
        if (trainingId == null || trainingId.length() == 0 || durationSeconds == null || durationSeconds < 1 || durationSeconds > 86400) {
            return;
        }
        targetTrainingId = trainingId;
        targetDurationSeconds = durationSeconds;
        targetConsumed = false;
        Properties.setValue("lapTrainingId", targetTrainingId);
        Properties.setValue("lapTargetSeconds", targetDurationSeconds);
        Properties.setValue("lapTargetConsumed", false);
        targetAcknowledgementPending = true;
    }

    private function updateTargetActivityState(timerState) {
        if (timerState == Activity.TIMER_STATE_ON) {
            if (Properties.getValue("lapTargetConsumed") == true) {
                targetTrainingId = "";
                targetDurationSeconds = 0;
                targetConsumed = true;
                Properties.setValue("lapTrainingId", "");
                Properties.setValue("lapTargetSeconds", 0);
            }
            wasRecording = true;
            Properties.setValue("lapWasRecording", true);
        } else if (timerState == Activity.TIMER_STATE_OFF && wasRecording) {
            targetConsumed = true;
            wasRecording = false;
            Properties.setValue("lapTargetConsumed", true);
            Properties.setValue("lapWasRecording", false);
        }
    }

    private function currentTargetDurationSeconds() {
        if (targetConsumed || targetTrainingId == "") {
            return 0;
        }
        return targetDurationSeconds;
    }

    private function transmitTargetAcknowledgement() {
        if (transferPending || targetTrainingId == "" || targetDurationSeconds <= 0) {
            return;
        }
        var acknowledgement = ["LAP_TARGET_ACK", 1, targetTrainingId, targetDurationSeconds];
        targetAcknowledgementPending = false;
        sendMessage("target_ack", acknowledgement);
    }

    function onUpdate(dc) {
        var width = dc.getWidth();
        var height = dc.getHeight();
        var scaleX = width / 390.0;
        var scaleY = height / 390.0;
        var displayTimerMillis = timerMillis;
        var displayDistanceMeters = distanceMeters;
        var displayPaceSeconds = paceSeconds;
        var displayHeartRate = heartRate;
        var displayRingProgress = progressRingFraction;
        if (LAYOUT_PREVIEW_MODE) {
            displayTimerMillis = 1714000;
            displayDistanceMeters = 4620.0;
            displayPaceSeconds = 372;
            displayHeartRate = 138;
            displayRingProgress = 4620.0 / 14500.0;
        }

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        if (backgroundBitmap != null) {
            dc.drawBitmap(
                (105 * scaleX).toNumber(),
                (60 * scaleY).toNumber(),
                backgroundBitmap
            );
        }

        var centerX = (width / 2).toNumber();
        var centerY = (height / 2).toNumber();
        drawProgressRing(dc, centerX, centerY, (180 * scaleX).toNumber(), displayRingProgress);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        drawMeasuredCentered(dc, formatClockTime(), Graphics.FONT_TINY, centerX, (50 * scaleY).toNumber(), 180 * scaleX, 28 * scaleY);
        drawElapsedTime(dc, formatTime(displayTimerMillis), centerX, (225 * scaleY).toNumber(), scaleX, scaleY);
        drawMeasuredCentered(dc, formatDistance(displayDistanceMeters), Graphics.FONT_MEDIUM, centerX, (272 * scaleY).toNumber(), 230 * scaleX, 34 * scaleY);
        drawPaceAndHeartRate(dc, (140 * scaleX).toNumber(), (250 * scaleX).toNumber(), (325 * scaleY).toNumber(), (350 * scaleY).toNumber(), displayPaceSeconds, displayHeartRate, scaleX, scaleY);
    }

    function onTransmitComplete(success) {
        transferPending = false;
        if (transferType == "target_ack") {
            System.println("LAP_TARGET_ACK callback=" + (success ? "complete" : "error"));
            return;
        }
        if (success) {
            transferSuccesses += 1;
        } else {
            transferErrors += 1;
        }
        System.println("LAP_TX callback=" + (success ? "complete" : "error") +
            " type=" + transferType + " attempts=" + transferAttempts +
            " success=" + transferSuccesses + " errors=" + transferErrors);
    }

    private function transmitCurrentSample() {
        if (PING_TEST_MODE) {
            if (pingSent || transferPending) {
                return;
            }
            pingSent = true;
            var ping = ["LAP_PING", 1, sequence];
            sequence += 1;
            sendMessage("PING", ping);
            return;
        }

        if (transferPending) {
            return;
        }
        var timestampSeconds = Time.now().value();
        var message = [
            1,
            sequence,
            timestampSeconds,
            timerMillis,
            distanceMeters,
            speedMetersPerSecond,
            heartRate,
            paused ? 2 : 3
        ];
        sequence += 1;
        sendMessage("v1", message);
    }

    private function sendMessage(type, message) {
        transferPending = true;
        transferType = type;
        transferStartedAt = Time.now().value();
        transferTimeoutReported = false;
        transferAttempts += 1;
        System.println("LAP_TX attempt type=" + type + " entries=" + message.size() +
            " attempts=" + transferAttempts);
        try {
            Communications.transmit(message, {}, new LapLiveTransmitListener(self));
        } catch (exception) {
            transferPending = false;
            transferErrors += 1;
            System.println("LAP_TX callback=exception type=" + type +
                " attempts=" + transferAttempts + " errors=" + transferErrors);
        }
    }

    private function checkTransferTimeout() {
        if (!transferPending || transferTimeoutReported) {
            return;
        }
        if (Time.now().value() - transferStartedAt < TRANSFER_TIMEOUT_SECONDS) {
            return;
        }
        transferTimeoutReported = true;
        System.println("LAP_TX callback=none type=" + transferType +
            " age_seconds=" + (Time.now().value() - transferStartedAt) +
            " attempts=" + transferAttempts + " success=" + transferSuccesses +
            " errors=" + transferErrors);
    }

    private function formatTime(milliseconds) {
        var totalSeconds = (milliseconds / 1000).toNumber();
        if (totalSeconds >= 3600) {
            var totalMinutes = (totalSeconds / 60).toNumber();
            return (totalSeconds / 3600).format("%d") + ":" + (totalMinutes % 60).format("%02d") + ":" + (totalSeconds % 60).format("%02d");
        }
        return (totalSeconds / 60).format("%d") + ":" + (totalSeconds % 60).format("%02d");
    }

    private function formatClockTime() {
        var clockTime = System.getClockTime();
        return clockTime.hour.format("%d") + ":" + clockTime.min.format("%02d");
    }

    private function formatDistance(meters) {
        if (meters < 0) {
            return "-- km";
        }
        return (meters / 1000.0).format("%.2f") + " km";
    }

    private function formatPace(seconds) {
        if (seconds <= 0) {
            return "--:--";
        }
        return (seconds / 60).format("%d") + ":" + (seconds % 60).format("%02d");
    }

    private function formatHeartRate(value) {
        if (value <= 0) {
            return "--";
        }
        return value.format("%d");
    }

    private function configuredTargetDistanceMeters() {
        var targetKm = Properties.getValue("targetDistanceKm");
        if (targetKm == null || targetKm <= 0) {
            return -1.0;
        }
        return targetKm * 1000.0;
    }

    private function drawProgressRing(dc, centerX, centerY, radius, progress) {
        progress = progress < 0 ? 0.0 : (progress > 1 ? 1.0 : progress);

        dc.setAntiAlias(true);
        dc.setPenWidth(6);
        dc.setColor(0x182018, Graphics.COLOR_TRANSPARENT);
        dc.drawCircle(centerX, centerY, radius);
        if (progress <= 0) {
            return;
        }

        dc.setColor(0x82D34A, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(6);
        var endAngle = (90 - 360 * progress).toNumber();
        while (endAngle < 0) {
            endAngle += 360;
        }
        dc.drawArc(centerX, centerY, radius, Graphics.ARC_CLOCKWISE, 90, endAngle);

        var radians = endAngle * Math.PI / 180.0;
        var capX = centerX + radius * Math.cos(radians);
        var capY = centerY - radius * Math.sin(radians);
        dc.setColor(0x82D34A, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(capX, capY, 3);
    }

    private function drawPaceAndHeartRate(dc, paceCenter, heartCenter, valueY, unitY, pace, pulse, scaleX, scaleY) {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        drawMeasuredCentered(dc, formatPace(pace), Graphics.FONT_MEDIUM, paceCenter, valueY, 100 * scaleX, 32 * scaleY);
        drawMeasuredCentered(dc, "/km", Graphics.FONT_XTINY, paceCenter, unitY, 72 * scaleX, 20 * scaleY);
        drawHeartRateValue(dc, formatHeartRate(pulse), heartCenter, valueY, 104 * scaleX, 32 * scaleY);
        drawMeasuredCentered(dc, "bpm", Graphics.FONT_XTINY, heartCenter, unitY, 72 * scaleX, 20 * scaleY);
        dc.setColor(0x252922, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(scaleX.toNumber());
        dc.drawLine((195 * scaleX).toNumber(), (314 * scaleY).toNumber(), (195 * scaleX).toNumber(), (360 * scaleY).toNumber());
    }

    private function drawHeartRateValue(dc, value, centerX, centerY, maxWidth, maxHeight) {
        if (value == "--") {
            drawMeasuredCentered(dc, value, Graphics.FONT_MEDIUM, centerX, centerY, maxWidth, maxHeight);
            return;
        }
        var font = chooseLargestFont(dc, value, maxWidth - 16, maxHeight, [Graphics.FONT_MEDIUM, Graphics.FONT_SMALL, Graphics.FONT_TINY]);
        var dimensions = dc.getTextDimensions(value, font);
        var valueWidth = dimensions[0];
        var heartSize = (11 * maxHeight / 32).toNumber();
        var gap = (4 * maxWidth / 104).toNumber();
        var startX = centerX - ((valueWidth + gap + heartSize) / 2).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(startX, centerY, font, value, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        drawHeart(dc, startX + valueWidth + gap, centerY - (heartSize / 2).toNumber(), heartSize);
    }

    private function drawHeart(dc, left, top, size) {
        var centerX = left + size / 2;
        var centerY = top + size / 2;
        var shoulder = (size * 0.27).toNumber();
        var waist = (size * 0.18).toNumber();
        var points = [
            [centerX, top + size],
            [left, centerY],
            [left, top + shoulder],
            [left + shoulder, top],
            [centerX - waist, top],
            [centerX, top + shoulder],
            [centerX + waist, top],
            [left + size - shoulder, top],
            [left + size, top + shoulder],
            [left + size, centerY]
        ];
        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        dc.fillPolygon(points);
    }

    private function drawElapsedTime(dc, text, centerX, centerY, scaleX, scaleY) {
        var maxWidth = 320 * scaleX;
        var maxHeight = 62 * scaleY;
        var font = chooseLargestFont(dc, text, maxWidth, maxHeight, [
            Graphics.FONT_NUMBER_HOT,
            Graphics.FONT_SYSTEM_NUMBER_HOT,
            Graphics.FONT_NUMBER_MEDIUM,
            Graphics.FONT_SYSTEM_NUMBER_MEDIUM,
            Graphics.FONT_NUMBER_MILD
        ]);
        drawMeasuredCentered(dc, text, font, centerX, centerY, maxWidth, maxHeight);
    }

    private function chooseLargestFont(dc, text, maxWidth, maxHeight, fonts) {
        for (var index = 0; index < fonts.size(); index += 1) {
            var font = fonts[index];
            var dimensions = dc.getTextDimensions(text, font);
            if (dimensions[0] <= maxWidth && dimensions[1] <= maxHeight) {
                return font;
            }
        }
        return fonts[fonts.size() - 1];
    }

    private function drawMeasuredCentered(dc, text, preferredFont, centerX, centerY, maxWidth, maxHeight) {
        var font = chooseLargestFont(dc, text, maxWidth, maxHeight, [preferredFont, Graphics.FONT_SMALL, Graphics.FONT_XTINY]);
        dc.drawText(centerX, centerY, font, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

}

class LapLiveTransmitListener extends Communications.ConnectionListener {
    private var dataField;

    function initialize(owner) {
        ConnectionListener.initialize();
        dataField = owner;
    }

    function onComplete() {
        dataField.onTransmitComplete(true);
    }

    function onError() {
        dataField.onTransmitComplete(false);
    }
}
