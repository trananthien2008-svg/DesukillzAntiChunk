package me.desukillz.antichunk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Các hàm tiện ích làm việc với gói tin, viết bằng reflection để không vỡ khi ProtocolLib đổi phiên bản. */
final class PacketUtil {

    private static final Object UNSAFE;
    private static final Method ALLOCATE;
    private static final Map<Class<?>, Field[]> FIELDS = new ConcurrentHashMap<>();

    static {
        Object unsafe = null;
        Method allocate = null;
        try {
            Class<?> c = Class.forName("sun.misc.Unsafe");
            Field f = c.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
            allocate = c.getMethod("allocateInstance", Class.class);
        } catch (Throwable ignored) {
            // copy() sẽ ném lỗi rõ ràng nếu cần dùng
        }
        UNSAFE = unsafe;
        ALLOCATE = allocate;
    }

    private PacketUtil() {
    }

    /** Lấy PacketType theo tên (null nếu phiên bản này không có hoặc không hỗ trợ). */
    static PacketType type(String name) {
        try {
            Object o = PacketType.Play.Server.class.getField(name).get(null);
            if (o instanceof PacketType t && t.isSupported()) {
                return t;
            }
        } catch (Throwable ignored) {
            // không có trong phiên bản này
        }
        return null;
    }

    static List<PacketType> types(String... names) {
        List<PacketType> list = new ArrayList<>();
        for (String name : names) {
            PacketType t = type(name);
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    static PacketType[] typeArray(String... names) {
        return types(names).toArray(new PacketType[0]);
    }

    /** Sao chép nông một object (không gọi constructor). */
    static Object shallowCopy(Object src) throws Exception {
        if (UNSAFE == null || ALLOCATE == null) {
            throw new IllegalStateException("Không truy cập được sun.misc.Unsafe");
        }
        Class<?> c = src.getClass();
        Object dst = ALLOCATE.invoke(UNSAFE, c);
        for (Field f : instanceFields(c)) {
            f.set(dst, f.get(src));
        }
        return dst;
    }

    /** Sao chép nông một gói tin để sửa mà không ảnh hưởng người chơi khác. */
    static PacketContainer copy(PacketContainer packet) throws Exception {
        return new PacketContainer(packet.getType(), shallowCopy(packet.getHandle()));
    }

    static Field[] instanceFields(Class<?> c) {
        return FIELDS.computeIfAbsent(c, k -> {
            List<Field> list = new ArrayList<>();
            for (Class<?> cur = k; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                for (Field f : cur.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        list.add(f);
                    }
                }
            }
            return list.toArray(new Field[0]);
        });
    }

    /** Vị trí section (toạ độ section, không phải block) của gói MULTI_BLOCK_CHANGE. */
    static BlockPosition sectionPos(PacketContainer packet) throws Exception {
        Object modifier = PacketContainer.class.getMethod("getSectionPositions").invoke(packet);
        Object value = ((StructureModifier<?>) modifier).readSafely(0);
        return value instanceof BlockPosition bp ? bp : null;
    }
}
