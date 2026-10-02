package script.conversation;

import script.*;
import script.library.ai_lib;
import script.library.utils;
import script.npc.entertainer_npc_session;

public class entertainer_buff_npc extends script.base_script
{
    public entertainer_buff_npc()
    {
    }
    public static final String SESSION_SCRIPT = "npc.entertainer_npc_session";
    public static final float BUFF_BUILDER_RANGE = 8.0f;
    public static final String SID_ALREADY_BEING_INSPIRED = "already_being_inspired";

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if ((!isTangible(self)) || (isPlayer(self)))
        {
            detachScript(self, "conversation.entertainer_buff_npc");
        }
        // Talk-only service NPC: invulnerable so a left-click engages the
        // conversation, not combat, plus the conversable condition.
        setInvulnerable(self, true);
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        setInvulnerable(self, true);
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info menuInfo) throws InterruptedException
    {
        int menu = menuInfo.addRootMenu(menu_info_types.CONVERSE_START, null);
        menu_info_data menuInfoData = menuInfo.getMenuItemById(menu);
        menuInfoData.setServerNotify(false);
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }

    public int OnIncapacitated(obj_id self, obj_id killer) throws InterruptedException
    {
        clearCondition(self, CONDITION_CONVERSABLE);
        detachScript(self, "conversation.entertainer_buff_npc");
        return SCRIPT_CONTINUE;
    }

    public int OnStartNpcConversation(obj_id self, obj_id player) throws InterruptedException
    {
        if (ai_lib.isInCombat(self) || ai_lib.isInCombat(player))
        {
            return SCRIPT_OVERRIDE;
        }
        if (!isIdValid(player) || isDead(player) || isIncapacitated(player))
        {
            npcEndConversation(player);
            return SCRIPT_CONTINUE;
        }
        if (utils.getBooleanScriptVar(player, entertainer_npc_session.VAR_IN_SESSION))
        {
            sendSystemMessage(player, new string_id("spam", SID_ALREADY_BEING_INSPIRED));
            npcEndConversation(player);
            return SCRIPT_CONTINUE;
        }
        float distance = (getLocation(self)).distance(getLocation(player));
        if (distance > BUFF_BUILDER_RANGE)
        {
            npcEndConversation(player);
            return SCRIPT_CONTINUE;
        }
        npcEndConversation(player);

        // Remember which NPC is buffing the player, so the completed buff can
        // read its expertise mods from the NPC (the player has none).
        utils.setScriptVar(player, entertainer_npc_session.VAR_NPC_ID, self);
        utils.setScriptVar(player, entertainer_npc_session.VAR_IN_SESSION, true);
        attachScript(player, SESSION_SCRIPT);
        // Ensure the NPC carries the master-entertainer values and that the
        // player's client sees the 20-point budget + 22 rows before the window opens.
        entertainer_npc_session.applyMasterMods(self);
        entertainer_npc_session.addClientMarkers(self, player);
        // Self-buff: the player is both buffer and recipient, so the player's own
        // client gets the Build-a-Buff window (a server NPC has no client to show
        // it on; making the NPC the buffer opened the Buffee window on the player).
        buffBuilderStart(player, player);
        return SCRIPT_CONTINUE;
    }
}
