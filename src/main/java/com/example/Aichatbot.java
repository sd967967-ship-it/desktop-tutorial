
package com.example;

import javax.sound.sampled.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class Aichatbot {

   private static final String MODEL_PATH = "D:\\vosk-model-small-en-us-0.15";
    
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int VOICE_LEVEL_THRESHOLD = 500;

    

    public static void main(String[] args) {

        System.out.println("================================");
        System.out.println("       Java Speech To Text");
        System.out.println("================================");

        // Disable unnecessary Vosk logging.
        try {
            Class<?> libVosk = Class.forName("org.vosk.LibVosk");
            Class<?> logLevel = Class.forName("org.vosk.LogLevel");
            @SuppressWarnings("unchecked")
            Object warnings = Enum.valueOf(
                    logLevel.asSubclass(Enum.class), "WARNINGS");
            libVosk.getMethod("setLogLevel", logLevel).invoke(null, warnings);
        } catch (ReflectiveOperationException e) {
            System.err.println("Vosk is not available. Add the Vosk Java dependency to the project.");
            return;
        }

        try {
            runSpeechRecognition();
        } catch (Exception e) {
            System.err.println("Speech recognition failed:");
            e.printStackTrace();
        }
    }

    private static void runSpeechRecognition()
            throws IOException, LineUnavailableException, ReflectiveOperationException, Exception {

        // Load speech recognition model.
        System.out.println("Loading speech model...");

        try (AutoCloseable model = (AutoCloseable) newInstance(
            "org.vosk.Model", MODEL_PATH)) {

            System.out.println("Model loaded successfully.");
            System.out.println();

            // Configure microphone.
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE,
                    16,
                    1,
                    2,
                    SAMPLE_RATE,
                    false
            );

            DataLine.Info info =
                    new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                throw new LineUnavailableException(
                        "Microphone/audio format is not supported."
                );
            }

            TargetDataLine microphone =
                    (TargetDataLine) AudioSystem.getLine(info);

            microphone.open(format);

            // Create recognizer.
                try (AutoCloseable recognizer = (AutoCloseable) newInstance(
                    "org.vosk.Recognizer", model, SAMPLE_RATE)) {

                microphone.start();

                System.out.println("--------------------------------");
                System.out.println("Microphone started.");
                System.out.println("Start speaking...");
                System.out.println("Press ENTER to stop.");
                System.out.println("--------------------------------");

                AtomicBoolean stopRequested = new AtomicBoolean(false);

                // Thread responsible for stopping recognition.
                Thread stopThread = new Thread(() -> {
                    try {
                        int input;
                        while ((input = System.in.read()) != -1) {
                            if (input == '\n' || input == '\r') {
                                stopRequested.set(true);
                                microphone.stop();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                stopThread.start();

                // Buffer for microphone data.
                byte[] buffer = new byte[4096];
                                long lastLevelReport = System.currentTimeMillis();

                  while (microphone.isOpen() && !stopRequested.get()) {

                    int bytesRead =
                            microphone.read(
                                    buffer,
                                    0,
                                    buffer.length
                            );

                    if (bytesRead <= 0) {
                        continue;
                    }

                    int peak = 0;
                    for (int index = 0; index + 1 < bytesRead; index += 2) {
                        int sample = Math.abs((short) ((buffer[index + 1] << 8)
                                | (buffer[index] & 0xff)));
                        peak = Math.max(peak, sample);
                    }
                    if (System.currentTimeMillis() - lastLevelReport >= 1000) {
                        String voiceStatus = peak >= VOICE_LEVEL_THRESHOLD
                            ? "VOICE DETECTED"
                            : "NO VOICE";
                        System.out.printf("\rMic: %-14s level: %5d    ",
                            voiceStatus, peak);
                        lastLevelReport = System.currentTimeMillis();
                    }

                    // Send microphone data to Vosk.
                        if ((Boolean) invoke(recognizer, "acceptWaveForm",
                            buffer, bytesRead)) {

                        String result =
                                (String) invoke(recognizer, "getResult");

                        String text =
                                extractText(result);

                        if (!text.isBlank()) {
                            System.out.println(
                                    "Recognized: " + text
                            );
                        }

                    } else {

                        // Partial result while speaking.
                        String partial =
                                extractPartialResult(
                                        (String) invoke(recognizer,
                                            "getPartialResult")
                                );

                        if (!partial.isBlank()) {
                            System.out.print(
                                    "\rListening: " + partial + "    "
                            );
                        }
                    }
                }

                // Get final result.
                String finalResult =
                        (String) invoke(recognizer, "getFinalResult");

                String finalText =
                        extractText(finalResult);

                if (!finalText.isBlank()) {
                    System.out.println();
                    System.out.println(
                            "Final: " + finalText
                    );
                }

                microphone.close();

                System.out.println();
                System.out.println("Speech recognition stopped.");
            }
        }
    }

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

    private static boolean areCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            if (args[index] == null || !wrap(parameterTypes[index]).isInstance(args[index])) {
                return false;
            }
        }
        return true;
    }

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
