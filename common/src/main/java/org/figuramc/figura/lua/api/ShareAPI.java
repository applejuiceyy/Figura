package org.figuramc.figura.lua.api;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.figuramc.figura.lua.api.entity.EntityAPI;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@LuaWhitelist
@LuaTypeDoc(
        name = "ShareAPI",
        value = "share"
)
public class ShareAPI {

    private final Avatar avatar;

    public ShareAPI(Avatar avatar) {
        this.avatar = avatar;
    }
    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = EntityAPI.class,
                            argumentNames = "entity"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {EntityAPI.class, String.class},
                            argumentNames = {"entity", "key"}
                    )
            },
            value = "share.get_variable"
    )
    public LuaValue getVarsForEntity(EntityAPI<?> entity, String keyOrNull) {
        entity.checkEntity();
        Avatar a = AvatarManager.getAvatar(entity.getEntity());
        LuaValue v;
        if (a == null || a.luaRuntime == null) {
            return keyOrNull != null ? LuaValue.NIL : new ReadOnlyLuaTable();
        } else {
            v = avatar.luaRuntime.boundaryManager.transform(a.luaRuntime.avatar_meta.storedStuff, a);
        }
        return keyOrNull == null ? v : v.get(keyOrNull);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload
            },
            value = "share.avatar_vars"
    )
    public LuaValue getAvatarVars() {
        LuaTable varList = new LuaTable();
        for (Avatar avatar : AvatarManager.getLoadedAvatars()) {
            varList.set(avatar.owner.toString(), avatar.luaRuntime == null ? new ReadOnlyLuaTable() : this.avatar.luaRuntime.boundaryManager.transform(avatar.luaRuntime.avatar_meta.storedStuff, avatar));
        }
        return varList;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {String.class, Object.class},
                    argumentNames = {"key", "value"}
            ),
            value = "share.store"
    )
    public ShareAPI store(@LuaNotNil String key, LuaValue value) {
        avatar.luaRuntime.avatar_meta.storedStuff.set(key, value == null ? LuaValue.NIL : value);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {String.class, Object.class},
                    argumentNames = {"key", "value"}
            ),
            value = "share.store"
    )
    public boolean isForeign(LuaValue value) {
        return avatar.luaRuntime.boundaryManager.isForeign(value);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {String.class, Object.class},
                    argumentNames = {"key", "value"}
            ),
            value = "share.store"
    )
    public boolean foreignValueIsAlive(LuaValue value) {
        return avatar.luaRuntime.boundaryManager.foreignValueIsAlive(value);
    }
}
