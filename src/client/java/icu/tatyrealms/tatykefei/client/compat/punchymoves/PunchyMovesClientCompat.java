package icu.tatyrealms.tatykefei.client.compat.punchymoves;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class PunchyMovesClientCompat {
    // 每帧渲染前重新设置，渲染结束后恢复，足够覆盖一次提交
    private static final long PREVIEW_MILLIS = 10_000L;
    private static Method look;
    private static Field lookValue;
    private static Field lookUntil;
    private static List<String> names = List.of();
    private static final LinkedList<Frame> frames = new LinkedList<>();

    private static final class Frame {
        final boolean pushed;
        final boolean restorable;
        final String look;
        final long until;

        Frame(boolean pushed, boolean restorable, String look, long until) {
            this.pushed = pushed;
            this.restorable = restorable;
            this.look = look;
            this.until = until;
        }
    }

    private static final Frame NOT_PUSHED = new Frame(false, false, null, 0);

    private PunchyMovesClientCompat() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("punchy_expressions")
                && !FabricLoader.getInstance().isModLoaded("punchy_moves")) {
            return;
        }
        try {
            Class<?> preview = Class.forName("punchymoves.client.face.FacePreview");
            Class<?> library = Class.forName("punchymoves.client.face.FaceLibrary");
            look = preview.getMethod("look", String.class, long.class);
            try {
                Field value = preview.getDeclaredField("look");
                value.setAccessible(true);
                Field until = preview.getDeclaredField("lookUntil");
                until.setAccessible(true);
                lookValue = value;
                lookUntil = until;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                lookValue = null;
                lookUntil = null;
            }
            try {
                names = copyNames(library.getMethod("lookNames").invoke(null));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LoggerFactory.getLogger("TSF").warn("Punchy expression names unavailable", exception);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            LoggerFactory.getLogger("TSF").warn("Punchy expressions integration unavailable", exception);
            look = null;
            names = List.of();
        }
    }

    public static boolean available() {
        return look != null;
    }

    public static List<String> lookNames() {
        return names;
    }

    public static void push(String face) {
        if (look == null || face == null || face.isEmpty()) {
            frames.push(NOT_PUSHED);
            return;
        }
        frames.push(snapshot());
        try {
            look.invoke(null, face, PREVIEW_MILLIS);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LoggerFactory.getLogger("TSF").warn("Failed to preview Punchy expression {}", face, exception);
        }
    }

    public static void pop() {
        if (frames.isEmpty()) {
            return;
        }
        Frame frame = frames.pop();
        if (!frame.pushed) {
            return;
        }
        if (frame.restorable) {
            try {
                lookValue.set(null, frame.look);
                lookUntil.setLong(null, frame.until);
                return;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LoggerFactory.getLogger("TSF").warn("Failed to restore Punchy expression", exception);
            }
        }
        try {
            look.invoke(null, null, 0L);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 表情会在预览时间结束后自动失效
        }
    }

    private static Frame snapshot() {
        if (lookValue == null || lookUntil == null) {
            return new Frame(true, false, null, 0);
        }
        try {
            return new Frame(true, true, (String) lookValue.get(null), lookUntil.getLong(null));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LoggerFactory.getLogger("TSF").warn("Failed to read Punchy expression state", exception);
            return new Frame(true, false, null, 0);
        }
    }

    private static List<String> copyNames(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String name && !name.isEmpty()) {
                result.add(name);
            }
        }
        return List.copyOf(result);
    }
}
