// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: Java, and only what the settings screen's rows need.
package dev.golemsclient.menu;

import dev.golemsclient.mixin.settings.OptionInstanceAccessor;
import dev.golemsclient.mixin.settings.TooltipAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one of Minecraft's options for a settings row: what it's called, what kind of control it gets (a switch, a
 * choice or a slider), the text for a value, and how to set it. Minecraft keeps the kinds of value private, so they're
 * found by name (the game's own names in development, Fabric's intermediary names when playing), like OneClient does.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class OptionValues {
    enum Kind { TOGGLE, CHOICE, SLIDER }

    private static final String OPTION = "net.minecraft.class_7172";
    private static final String SLIDERABLE = "net.minecraft.class_7172$class_7176";
    private static final String CYCLEABLE = "net.minecraft.class_7172$class_7305";
    private static final String SLIDER_OR_CYCLE = "net.minecraft.class_7172$class_7306";
    private static final String INT_RANGE = "net.minecraft.class_7172$class_7275";
    private static final String SLIDERABLE_ENUM = "net.minecraft.class_7172$class_12139";
    private static final String SETTER = "net.minecraft.class_7172$class_7305$class_7274";

    private static final Class<?> sliderableType = type(SLIDERABLE, "net.minecraft.client.OptionInstance$SliderableValueSet");
    private static final Class<?> cycleableType = type(CYCLEABLE, "net.minecraft.client.OptionInstance$CycleableValueSet");
    private static final Class<?> sliderOrCycleType = type(SLIDER_OR_CYCLE, "net.minecraft.client.OptionInstance$SliderableOrCyclableValueSet");
    private static final Class<?> intRangeType = type(INT_RANGE, "net.minecraft.client.OptionInstance$IntRangeBase");
    private static final Class<?> sliderableEnumType = type(SLIDERABLE_ENUM, "net.minecraft.client.OptionInstance$SliderableEnum");
    private static final Class<?> setterType = type(SETTER, "net.minecraft.client.OptionInstance$CycleableValueSet$ValueSetter");

    private static final Method valuesMethod = method(OptionInstance.class, OPTION, "method_41754", "values", "()Lnet/minecraft/class_7172$class_7178;");
    private static final Method toSliderMethod = method(sliderableType, SLIDERABLE, "method_41765", "toSliderValue", "(Ljava/lang/Object;)D", Object.class);
    private static final Method fromSliderMethod = method(sliderableType, SLIDERABLE, "method_41763", "fromSliderValue", "(D)Ljava/lang/Object;", double.class);
    private static final Method applyImmediatelyMethod = method(sliderableType, SLIDERABLE, "comp_2661", "applyValueImmediately", "()Z");
    private static final Method listSupplierMethod = method(cycleableType, CYCLEABLE, "method_42721", "valueListSupplier", "()Lnet/minecraft/class_5676$class_5680;");
    private static final Method valueSetterMethod = method(cycleableType, CYCLEABLE, "comp_674", "valueSetter", "()Lnet/minecraft/class_7172$class_7305$class_7274;");
    private static final Method createCycleButtonMethod = method(sliderOrCycleType, SLIDER_OR_CYCLE, "method_42722", "createCycleButton", "()Z");
    private static final Method minInclusiveMethod = method(intRangeType, INT_RANGE, "comp_593", "minInclusive", "()I");
    private static final Method maxInclusiveMethod = method(intRangeType, INT_RANGE, "comp_594", "maxInclusive", "()I");
    private static final Method enumValuesMethod = method(sliderableEnumType, SLIDERABLE_ENUM, "comp_5052", "values", "()Ljava/util/List;");
    private static final Method setterCall = setterMethod();
    private static final Field initialValue = initialField();

    private OptionValues() {
    }

    /** What an option is, read once: its name, its kind of control and (for choices) what it can be. Null if unknown. */
    record Info(OptionInstance<Object> option, String caption, String hint, Kind kind, Object values, List<Object> choices,
                int min, int max, boolean live) {
    }

    static Info read(OptionInstance<?> instance) {
        OptionInstance<Object> option = (OptionInstance<Object>) instance;
        Object values = call(valuesMethod, option);
        if (values == null) return null;
        String caption = caption(option);
        String hint = hint(option);
        boolean live = Boolean.TRUE.equals(call(applyImmediatelyMethod, values));
        if (is(sliderOrCycleType, values) && Boolean.FALSE.equals(call(createCycleButtonMethod, values))) {
            return slider(option, caption, hint, values, live);
        }
        if (is(cycleableType, values)) {
            Object supplier = call(listSupplierMethod, values);
            List<Object> list = supplier instanceof CycleButton.ValueListSupplier<?> s ? (List<Object>) s.getSelectedList() : null;
            if (list == null || list.isEmpty()) return null;
            Object current = option.get();
            if (current instanceof Boolean && list.size() == 2 && list.stream().allMatch(v -> v instanceof Boolean)) {
                return new Info(option, caption, hint, Kind.TOGGLE, values, list, 0, 0, true);
            }
            return new Info(option, caption, hint, Kind.CHOICE, values, list, 0, 0, true);
        }
        if (is(sliderableEnumType, values)) {
            Object list = call(enumValuesMethod, values);
            if (list instanceof List<?> l && !l.isEmpty()) return new Info(option, caption, hint, Kind.CHOICE, values, (List<Object>) l, 0, 0, true);
        }
        if (is(sliderableType, values)) return slider(option, caption, hint, values, live);
        return null;
    }

    private static Info slider(OptionInstance<Object> option, String caption, String hint, Object values, boolean live) {
        if (is(intRangeType, values)) {
            Object min = call(minInclusiveMethod, values), max = call(maxInclusiveMethod, values);
            if (min instanceof Integer lo && max instanceof Integer hi) return new Info(option, caption, hint, Kind.SLIDER, values, null, lo, hi, live);
        }
        return new Info(option, caption, hint, Kind.SLIDER, values, null, 0, 0, live);
    }

    // ------------------------------------------------------------------ values

    /** Where the option is on its slider, 0 to 1. */
    static double toSlider(Info info, Object value) {
        if (info.max() > info.min() && value instanceof Integer i) return (i - info.min()) / (double) (info.max() - info.min());
        Object v = call(toSliderMethod, info.values(), value);
        return v instanceof Double d ? d : 0.0;
    }

    /** The value at a point on the slider (0 to 1). */
    static Object fromSlider(Info info, double slider) {
        slider = Math.max(0.0, Math.min(1.0, slider));
        if (info.max() > info.min()) return (int) Math.round(info.min() + slider * (info.max() - info.min()));
        return call(fromSliderMethod, info.values(), slider);
    }

    static void set(Info info, Object value) {
        if (value == null) return;
        if (info.kind() != Kind.SLIDER && is(cycleableType, info.values())) {
            Object setter = call(valueSetterMethod, info.values());
            if (setter != null && setterCall != null) {
                try {
                    setterCall.invoke(setter, info.option(), value);
                    return;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        info.option().set(value);
    }

    static Object defaultValue(OptionInstance<Object> option) {
        if (initialValue == null) return null;
        try {
            return initialValue.get(option);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** The words for a value ("Fancy", "70", "ON"), without the option's name in front. */
    static String valueText(Info info, Object value) {
        Component component = label(info.option(), value);
        if (component == null) return String.valueOf(value);
        String cut = captionless(component, info.option());
        if (cut != null) return cut;
        String full = component.getString();
        for (String prefix : new String[] {info.caption(), info.option().toString()}) {
            if (!prefix.isEmpty() && full.startsWith(prefix)) {
                String rest = trim(full.substring(prefix.length()));
                return rest.isEmpty() ? full : rest;
            }
        }
        return full;
    }

    // ------------------------------------------------------------------ names

    private static String key(OptionInstance<?> option) {
        try {
            Component c = ((OptionInstanceAccessor) (Object) option).golemsclient$caption();
            if (c != null && c.getContents() instanceof TranslatableContents t) return t.getKey();
        } catch (RuntimeException ignored) {
        }
        return option.toString();
    }

    private static String caption(OptionInstance<Object> option) {
        String plain = option.toString();
        String key = key(option);
        boolean missing = !key.equals(plain) ? !Language.getInstance().has(key) : plain.matches("^[a-z0-9_]+(\\.[A-Za-z0-9_]+)+$");
        if (!missing) return plain;
        Component sample = label(option, option.get());
        String text = sample == null ? "" : sample.getString();
        int colon = text.indexOf(':');
        String prefix = colon > 0 ? text.substring(0, colon).trim() : "";
        return prefix.isEmpty() ? plain : prefix;
    }

    private static String hint(OptionInstance<Object> option) {
        try {
            Object tooltip = ((OptionInstanceAccessor) (Object) option).golemsclient$tooltip().apply(option.get());
            if (tooltip == null) return null;
            String s = ((TooltipAccessor) tooltip).golemsclient$message().getString();
            return s.isBlank() ? null : s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Component label(OptionInstance<Object> option, Object value) {
        try {
            return ((OptionInstanceAccessor) (Object) option).golemsclient$valueLabel().apply(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String captionless(Component component, OptionInstance<Object> option) {
        if (!(component.getContents() instanceof TranslatableContents contents)) return null;
        Object[] args = contents.getArgs();
        if (args.length == 0) return null;
        String captionKey = key(option);
        boolean matches = args[0] instanceof Component first && ((first.getContents() instanceof TranslatableContents t && t.getKey().equals(captionKey))
                || first.getString().equals(option.toString()));
        if (!matches) return null;
        Object[] rest = new Object[args.length];
        rest[0] = "";
        System.arraycopy(args, 1, rest, 1, args.length - 1);
        String text = trim(Component.translatable(contents.getKey(), rest).getString());
        return text.isEmpty() ? null : text;
    }

    private static String trim(String text) {
        String t = text.trim();
        int i = 0;
        while (i < t.length() && (t.charAt(i) == ':' || t.charAt(i) == '：' || Character.isWhitespace(t.charAt(i)) || t.charAt(i) == ' ')) i++;
        return t.substring(i).trim();
    }

    // ------------------------------------------------------------------ reflection

    private static boolean is(Class<?> type, Object value) {
        return type != null && type.isInstance(value);
    }

    private static Object call(Method method, Object target, Object... args) {
        if (method == null || target == null) return null;
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Class<?> type(String intermediary, String named) {
        try {
            return Class.forName(named);
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", intermediary));
        } catch (Throwable e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String intermediaryOwner, String intermediary, String named, String descriptor, Class<?>... params) {
        if (owner == null) return null;
        List<String> names = new ArrayList<>();
        names.add(named);
        try {
            names.add(FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", intermediaryOwner, intermediary, descriptor));
        } catch (Throwable ignored) {
        }
        for (String name : names) {
            try {
                Method m = owner.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method setterMethod() {
        if (setterType == null) return null;
        for (Method m : setterType.getDeclaredMethods()) {
            if (Modifier.isAbstract(m.getModifiers())) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Field initialField() {
        List<String> names = new ArrayList<>();
        names.add("initialValue");
        try {
            names.add(FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", OPTION, "field_37866", "Ljava/lang/Object;"));
        } catch (Throwable ignored) {
        }
        for (String name : names) {
            try {
                Field f = OptionInstance.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static void save() {
        Minecraft.getInstance().options.save();
    }
}
