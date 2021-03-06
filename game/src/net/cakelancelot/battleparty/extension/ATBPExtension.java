package net.cakelancelot.battleparty.extension;

import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.extensions.SFSExtension;
import net.cakelancelot.battleparty.extension.evthandlers.JoinRoomEventHandler;
import net.cakelancelot.battleparty.extension.evthandlers.JoinZoneEventHandler;
import net.cakelancelot.battleparty.extension.reqhandlers.ClientReadyHandler;
import net.cakelancelot.battleparty.extension.reqhandlers.MoveActor;
import net.cakelancelot.battleparty.extension.reqhandlers.PingHandler;
import net.cakelancelot.battleparty.extension.reqhandlers.Stub;

public class ATBPExtension extends SFSExtension {
    @Override
    public void init() {
        this.addEventHandler(SFSEventType.USER_JOIN_ROOM, JoinRoomEventHandler.class);
        this.addEventHandler(SFSEventType.USER_JOIN_ZONE, JoinZoneEventHandler.class);

        this.addRequestHandler("req_hit_actor", Stub.class);
        this.addRequestHandler("req_keep_alive", Stub.class);
        this.addRequestHandler("req_goto_room", Stub.class);
        this.addRequestHandler("req_move_actor", MoveActor.class);
        this.addRequestHandler("req_delayed_login", Stub.class);
        this.addRequestHandler("req_buy_item", Stub.class);
        this.addRequestHandler("req_pickup_item", Stub.class);
        this.addRequestHandler("req_do_actor_ability", Stub.class);
        this.addRequestHandler("req_console_message", Stub.class);
        this.addRequestHandler("req_mini_map_message", PingHandler.class);
        this.addRequestHandler("req_use_spell_point", Stub.class);
        this.addRequestHandler("req_reset_spell_points", Stub.class);
        this.addRequestHandler("req_toggle_auto_level", Stub.class);
        this.addRequestHandler("req_client_ready", ClientReadyHandler.class);
        this.addRequestHandler("req_dump_player", Stub.class);
        this.addRequestHandler("req_auto_target", Stub.class);
        trace("ATBP Extension loaded");
    }
}
