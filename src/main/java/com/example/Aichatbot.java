
package com.example;

import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class Aichatbot {

    // Location of the English Vosk model used for speech recognition.
    private static final String MODEL_PATH = "D:\\vosk-model-small-en-us-0.15";

    // Audio settings expected by the Vosk recognizer.
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final int FRAME_SIZE = 2;

    // Signal level above which the live indicator considers sound to be voice.
    private static final int VOICE_LEVEL_THRESHOLD = 500;

    // Safety limit so recording cannot continue forever if keyboard input fails.
    private static final int MAX_RECORDING_SECONDS = 60;

    // Starts the application and handles top-level recognition errors.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VoiceRecorderFrame().setVisible(true));
    }

    // Prints the application title when the program starts.
    private static void printBanner() {
        System.out.println("================================");
        System.out.println("       Java Speech To Text");
        System.out.println("================================");
    }

    // Reduces Vosk's native-library logging and reports a missing dependency.
    private static boolean configureVoskLogging() {
        try {
            Class<?> libVosk = Class.forName("org.vosk.LibVosk");
            Class<?> logLevel = Class.forName("org.vosk.LogLevel");
            @SuppressWarnings("unchecked")
            Object warnings = Enum.valueOf(
                    logLevel.asSubclass(Enum.class), "WARNINGS");
            libVosk.getMethod("setLogLevel", logLevel).invoke(null, warnings);
            return true;
        } catch (ReflectiveOperationException e) {
            System.err.println("Vosk is not available. Add the Vosk Java dependency to the project.");
            return false;
        }
    }

    // Loads the model, opens the microphone, and manages the recognizer lifetime.
    private static void runSpeechRecognition()
            throws Exception {
        System.out.println("Loading speech model...");

        try (AutoCloseable model = (AutoCloseable) newInstance(
                "org.vosk.Model", MODEL_PATH)) {
            System.out.println("Model loaded successfully.");
            System.out.println();

            try (TargetDataLine microphone = openMicrophone();
                    AutoCloseable recognizer = (AutoCloseable) newInstance(
                            "org.vosk.Recognizer", model, SAMPLE_RATE)) {
                recognizeMicrophone(microphone, recognizer);
            }
        }
    }

    // Creates and opens a microphone using the format required by Vosk.
    private static TargetDataLine openMicrophone()
            throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                FRAME_SIZE,
                SAMPLE_RATE,
                false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException(
                    "Microphone/audio format is not supported.");
        }
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        return microphone;
    }

    // Reads microphone audio until the user presses Enter and feeds it to Vosk.
    private static void recognizeMicrophone(TargetDataLine microphone,
            Object recognizer) throws ReflectiveOperationException {
        microphone.start();
        printRecordingInstructions();

        AtomicBoolean stopRequested = new AtomicBoolean(false);
        startStopListener(microphone, stopRequested);
        byte[] buffer = new byte[4096];
        long lastLevelReport = System.currentTimeMillis();

        while (microphone.isOpen() && !stopRequested.get()) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead <= 0) {
                continue;
            }
            lastLevelReport = reportAudioLevel(buffer, bytesRead, lastLevelReport);
            processAudio(recognizer, buffer, bytesRead);
        }

        printFinalResult(recognizer);
        microphone.close();
        System.out.println();
        System.out.println("Speech recognition stopped.");
    }

    // Tells the user how to interact with the running recognizer.
    private static void printRecordingInstructions() {
        System.out.println("--------------------------------");
        System.out.println("Microphone started.");
        System.out.println("Start speaking...");
        System.out.println("Press ENTER to stop.");
        System.out.println("--------------------------------");
    }

    // Watches standard input and stops recording when Enter or Q is pressed.
    private static void startStopListener(TargetDataLine microphone,
            AtomicBoolean stopRequested) {
        Thread inputThread = new Thread(() -> {
            try {
                int input;
                while ((input = System.in.read()) != -1) {
                    if (input == '\n' || input == '\r'
                            || input == 'q' || input == 'Q') {
                        stopRequested.set(true);
                        microphone.stop();
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not read stop command: " + e.getMessage());
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();

        // Stops recording automatically if the input thread never receives a key.
        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(MAX_RECORDING_SECONDS * 1000L);
                if (stopRequested.compareAndSet(false, true)) {
                    System.out.println("\nMaximum recording time reached.");
                    microphone.stop();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    // Calculates and displays the current microphone signal level once per second.
    private static long reportAudioLevel(byte[] buffer, int bytesRead,
            long lastLevelReport) {
        int peak = findPeakLevel(buffer, bytesRead);
        long now = System.currentTimeMillis();
        if (now - lastLevelReport >= 1000) {
            String voiceStatus = peak >= VOICE_LEVEL_THRESHOLD
                    ? "VOICE DETECTED"
                    : "NO VOICE";
            System.out.printf("\rMic: %-14s level: %5d    ", voiceStatus, peak);
            return now;
        }
        return lastLevelReport;
    }

    // Finds the loudest 16-bit PCM sample in one microphone buffer.
    private static int findPeakLevel(byte[] buffer, int bytesRead) {
        int peak = 0;
        for (int index = 0; index + 1 < bytesRead; index += 2) {
            int sample = Math.abs((short) ((buffer[index + 1] << 8)
                    | (buffer[index] & 0xff)));
            peak = Math.max(peak, sample);
        }
        return peak;
    }

    // Sends one audio buffer to Vosk and prints complete or partial text.
    private static void processAudio(Object recognizer, byte[] buffer,
            int bytesRead) throws ReflectiveOperationException {
        boolean complete = (Boolean) invoke(recognizer, "acceptWaveForm",
                buffer, bytesRead);
        if (complete) {
            printRecognizedText((String) invoke(recognizer, "getResult"));
        } else {
            printPartialText((String) invoke(recognizer, "getPartialResult"));
        }
    }

    // Prints a completed phrase returned by Vosk.
    private static void printRecognizedText(String json) {
        String text = extractText(json);
        if (!text.isBlank()) {
            System.out.println("\nRecognized: " + text);
        }
    }

    // Prints the in-progress phrase returned by Vosk.
    private static void printPartialText(String json) {
        String partial = extractPartialResult(json);
        if (!partial.isBlank()) {
            System.out.print("\rListening: " + partial + "    ");
        }
    }

    // Prints any words Vosk has buffered when recording ends.
    private static void printFinalResult(Object recognizer)
            throws ReflectiveOperationException {
        String finalText = extractText(
                (String) invoke(recognizer, "getFinalResult"));
        if (!finalText.isBlank()) {
            System.out.println("\nFinal: " + finalText);
        }
    }

    // Extracts completed speech text from Vosk's JSON response.
    private static String extractText(String json) {

        // Vosk returns JSON such as:
        // {"text" : "hello world"}

        int start = json.indexOf("\"text\"");
        if (start == -1) {
            return "";
        }

        start = json.indexOf(':', start);

        if (start == -1) {
            return "";
        }

        start++;

        int end = json.indexOf('}', start);

        if (end == -1) {
            return "";
        }

        String text =
                json.substring(start, end)
                    .replace("\"", "")
                    .trim();

        return text;
    }

    // Creates a Vosk object through reflection so the dependency stays optional at compile time.
    private static Object newInstance(String className, Object... args)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        for (var constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == args.length) {
                return constructor.newInstance(args);
            }
        }
        throw new NoSuchMethodException(className);
    }

    // Calls a Vosk method while selecting the overload compatible with the arguments.
    private static Object invoke(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == args.length
                    && areCompatible(method.getParameterTypes(), args)) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    // Checks whether runtime argument types match a reflected method signature.
    private static boolean areCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            if (args[index] == null || !wrap(parameterTypes[index]).isInstance(args[index])) {
                return false;
            }
        }
        return true;
    }

    // Converts primitive types to wrapper types for reflective type checks.
    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    // Extracts in-progress speech text from Vosk's partial JSON response.
    private static String extractPartialResult(String json) {

        // Vosk returns:
        // {"partial" : "hello wor"}

        int start = json.indexOf("\"partial\"");
        if (start == -1) {
            return "";
        }

        start = json.indexOf(':', start);

        if (start == -1) {
            return "";
        }

        start++;

        int end = json.indexOf('}', start);

        if (end == -1) {
            return "";
        }

        return json.substring(start, end)
                .replace("\"", "")
                .trim();
    }
}
