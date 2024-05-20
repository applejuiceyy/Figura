package org.figuramc.figura.lua;

import org.figuramc.figura.avatar.Avatar;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.function.Function;
import java.util.function.Supplier;

public class BoundaryManager {
    private final Avatar owner;
    private final LuaTable bindings;

    public BoundaryManager(Avatar owner) {
        this.owner = owner;
        bindings = new LuaTable();
        LuaTable meta = new LuaTable();
        meta.set("__mode", "kv");
        bindings.setmetatable(meta);
    }

    public Varargs transform(Varargs value, Avatar from) {
        if(value.narg() == 1) {
            return transform(value.arg1(), from);
        }
        return LuaValue.varargsOf(transformToArray(value, from));
    }

    public boolean isForeign(LuaValue value) {
        return value instanceof Foreign;
    }

    public boolean foreignValueIsAlive(LuaValue value) {
        return value instanceof Foreign f && f.isAlive();
    }

    private LuaValue[] transformToArray(Varargs value, Avatar from) {
        if(value.narg() == 1) {
            return new LuaValue[]{transform(value.arg1(), from)};
        }

        LuaValue[] transformed = new LuaValue[value.narg()];
        for (int i = 1; i <= value.narg(); i++) {
            transformed[i - 1] = transform(value.arg(i), from);
        }
        return transformed;
    }

    public LuaValue transform(LuaValue value, Avatar from) {
        LuaValue simpled = simpleTransform(value);
        if(simpled != null) {
            return simpled;
        }
        if(owner == from) {
            LuaValue shortcut = shortcutTransform(value);
            if(shortcut != null) {
                return shortcut;
            }
        }
        if (value.isuserdata()) {
            BoundaryManager.Transformable data = (BoundaryManager.Transformable) value.checkuserdata(BoundaryManager.Transformable.class);
            if(data != null) {
                return owner.luaRuntime.typeManager.javaToLua(data.transform(data, from, owner)).arg1();
            }
            return LuaValue.NIL;
        }
        LuaValue luaValue = bindings.get(value);
        if(!luaValue.isnil()) {
            return luaValue;
        }
        LuaValue transformed = transformAfterCache(value, from);
        bindings.set(value, transformed);
        from.luaRuntime.boundaryManager.bindings.set(transformed, value);
        return transformed;
    }

    private LuaValue simpleTransform(LuaValue value) {
        if (value.isnil() || value.isnumber() || value.isstring() || value.isboolean()) {
            return value;
        }
        return null;
    }

    private LuaValue shortcutTransform(LuaValue value) {
        if (value.isfunction()) {
            return value;
        }
        return null;
    }

    private LuaValue transformAfterCache(LuaValue value, Avatar from) {
        if(value instanceof ForeignFunction f) {
            return new ForeignFunction(from, f.value);
        }
        if (value.istable()) {
            return new ForeignTable(value, from);
        }
        else if (value.isfunction()) {
            return new ForeignFunction(from, value);
        }

        return LuaValue.NIL;
    }

    private boolean avatarIsAlive(Avatar avatar) {
        return !avatar.scriptError && avatar.luaRuntime != null && avatar.loaded;
    }
    public interface Transformable {
        Object transform(Object value, Avatar avatar, Avatar into);
    }

    interface Foreign {
        boolean isAlive();
    }

    public class ForeignTable extends ReadOnlyLuaTable implements Foreign {
        private final LuaValue value;
        private final Avatar from;

        public ForeignTable(LuaValue value, Avatar from) {
            this.value = value;
            this.from = from;
        }

        @Override
        public LuaValue get(LuaValue key) {

            if(!securityCheck("__foreign_get", key, true, true)) {
                return NIL;
            }
            // may cause code execution
            return doContained(() -> translate(value::get, key));
        }

        @Override
        public LuaValue rawget(int key) {
            if(!securityCheck("__foreign_get", LuaValue.valueOf(key), true, true)) {
                return NIL;
            }
            return transform(value.rawget(key), from);
        }

        @Override
        public LuaValue rawget(LuaValue key) {
            if(!securityCheck("__foreign_get", key, true, true)) {
                return NIL;
            }
            return translate(value::rawget, key);
        }

        @Override
        public Varargs next(LuaValue key) {
            if(!avatarIsAlive(from)) {
                return NIL;
            }
            Varargs next = value.next(from.luaRuntime.boundaryManager.transform(key, owner));
            if(next.arg1().isnil()) return NIL;
            while(true) {
                while(!securityCheck("__foreign_get", next.arg1(), true, true)) {
                    next = value.next(next.arg1());
                    if(next.arg1().isnil()) return NIL;
                }
                Varargs transformed = transform(next, from);
                if(transformed.arg1().isnil() || transformed.arg(2).isnil()) continue;
                return transformed;
            }

        }

        @Override
        public void set(int key, LuaValue value) {
            if(!securityCheck("__foreign_set", LuaValue.valueOf(key), true, false)) {
                return;
            }
            doContained(() -> {
                value.set(key, from.luaRuntime.boundaryManager.transform(value, owner));
                return NIL;
            });
        }

        @Override
        public void set(LuaValue key, LuaValue value) {
            if(!securityCheck("__foreign_set", key, true, false)) {
                return;
            }
            doContained(() -> {
                value.set(key, from.luaRuntime.boundaryManager.transform(value, owner));
                return NIL;
            });
        }

        @Override
        public void rawset(int key, LuaValue value) {
            if(!securityCheck("__foreign_set", LuaValue.valueOf(key), true, false)) {
                return;
            }
            value.rawset(key, from.luaRuntime.boundaryManager.transform(value, owner));
        }

        @Override
        public void rawset(LuaValue key, LuaValue value) {
            if(!securityCheck("__foreign_set", key, true, false)) {
                return;
            }
            value.rawset(key, from.luaRuntime.boundaryManager.transform(value, owner));
        }

        @Override
        public Varargs inext(LuaValue key) {
            key.checkint(); // shortcut
            return translate(super::inext, key);
        }

        @Override
        public int rawlen() {
            if(!avatarIsAlive(from)) {
                return 0;
            }
            if(value.metatag(LuaValue.valueOf("__foreign_get")).arg1().isnil()) {
                return value.rawlen();
            }

            int n = 0;
            //noinspection StatementWithEmptyBody
            while(!rawget(++n).isnil()) {}
            return n - 1;
        }

        @Override
        public String tojstring() {
            return typename() + ": foreign";
        }

        public  <I extends Varargs, O extends Varargs> O translate(Function<I, O> consumer, I input) {
            // luaj doesn't bode really well with generic usage,
            // but it's used here for simplicity
            // it's expected for transform to give somewhat the same types anyway
            if(!from.scriptError && from.luaRuntime != null && from.loaded) {
                //noinspection unchecked
                return (O) transform(consumer.apply((I) from.luaRuntime.boundaryManager.transform(input, owner)), from);
            }
            //noinspection unchecked
            return (O) LuaValue.NIL;
        }

        public boolean securityCheck(String metaName, Varargs vars, boolean allowTable, boolean defaultValue) {
            if(!avatarIsAlive(from)) {
                return defaultValue;
            }
            LuaValue luaValue = value.metatag(LuaValue.valueOf(metaName)).arg1();
            LuaValue val = LuaValue.valueOf(defaultValue);
            if(luaValue.istable()) {
                if(allowTable) {
                    Varargs transform = from.luaRuntime.boundaryManager.transform(vars, owner);
                    val = luaValue.rawget(transform.arg1());
                }
            }
            else if(luaValue.isfunction()) {
                try {
                    if(from.luaRuntime.isRunning()) {
                        Varargs transform = from.luaRuntime.boundaryManager.transform(vars, owner);
                        val = luaValue.invoke(transform).arg1();
                    }
                    else {
                        LuaValue[] transform = from.luaRuntime.boundaryManager.transformToArray(vars, owner);
                        val = from.runWithoutCapture(luaValue, from.tick, (Object[]) transform).arg1();
                    }
                } catch (Exception | StackOverflowError e) {
                    FiguraLuaPrinter.sendLuaError(FiguraLuaRuntime.parseError(e), from);
                }
            }
            return val.toboolean();
        }

        private LuaValue doContained(Supplier<LuaValue> supplier) {
            try {
                if(from.luaRuntime.isRunning()) {
                    return supplier.get();
                }
                else {
                    return from.runWithoutCapture(new ZeroArgFunction() {
                        @Override
                        public LuaValue call() {
                            return supplier.get();
                        }
                    }, from.tick).arg1();
                }
            } catch (Exception | StackOverflowError e) {
                FiguraLuaPrinter.sendLuaError(FiguraLuaRuntime.parseError(e), from);
                return NIL;
            }
        }

        public LuaValue getForeignValue() {
            return value;
        }

        public LuaValue foreignGetMetatable() {
            if(!avatarIsAlive(from)) {
                return NIL;
            }
            LuaValue metatable = value.metatag(LuaValue.valueOf("__foreign_metatable")).arg1();
            if (!metatable.isnil()) {
                return transform(metatable, from);
            }
            return NIL;
        }

        public void foreignSetMetatable(LuaValue newMeta) {
            if(!avatarIsAlive(from)) {
                return;
            }
            LuaValue metatable = value.metatag(LuaValue.valueOf("__foreign_set_metatable")).arg1();
            if (!metatable.toboolean()) {
                return;
            }
            value.setmetatable(from.luaRuntime.boundaryManager.transform(newMeta, owner));
        }

        @Override
        public boolean isAlive() {
            return avatarIsAlive(from);
        }
    }

    private class ForeignFunction extends VarArgFunction implements Foreign{
        private final Avatar from;
        private final LuaValue value;

        public ForeignFunction(Avatar from, LuaValue value) {
            this.from = from;
            this.value = value;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (!avatarIsAlive(from)) {
                return varargsOf(FALSE, valueOf("Avatar has errored"));
            }

            Varargs run = null;

            try {
                if(from.luaRuntime.isRunning()) {
                    Varargs args1 = from.luaRuntime.boundaryManager.transform(args, owner);
                    run = value.invoke(args1);
                }
                else {
                    LuaValue[] args1 = from.luaRuntime.boundaryManager.transformToArray(args, owner);
                    run = from.runWithoutCapture(value, from.tick, (Object[]) args1);
                }
            } catch (Exception | StackOverflowError e) {
                FiguraLuaPrinter.sendLuaError(FiguraLuaRuntime.parseError(e), from);
            }

            if(run == null) {
                return varargsOf(FALSE, valueOf("Function call errored"));
            }
            return LuaValue.varargsOf(valueOf(true), transform(run, from));
        }

        @Override
        public String tojstring() {
            return "function: foreign";
        }

        @Override
        public boolean isAlive() {
            return avatarIsAlive(from);
        }
    }
}
