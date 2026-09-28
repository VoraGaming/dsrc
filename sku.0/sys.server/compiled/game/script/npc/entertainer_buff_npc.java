package script.npc;

import script.*;
import script.library.*;

public class entertainer_buff_npc extends script.base_script
{
    public entertainer_buff_npc()
    {
    }
    public static final String NPC_NAME = "Twi'lek Entertainer";
    public static final float MAX_USE_RANGE = 10.0f;
    public static final float INSPIRATION_DURATION = 3600.0f;
    public static final String SCRIPTVAR_PID = "entertainer_npc.pid";
    public static final String OBJVAR_MAX_BUDGET_20 = "entertainer_npc.max_budget_20";
    public static final String SCRIPT_BUFF_BUILDER_RESPONSE = "systems.buff_builder.buff_builder_response";
    public static final String SCRIPT_BUFF_BUILDER_CANCEL = "systems.buff_builder.buff_builder_cancel";
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setupEntertainer(self);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setupEntertainer(self);
        return SCRIPT_CONTINUE;
    }
    public void setupEntertainer(obj_id self) throws InterruptedException
    {
        setName(self, NPC_NAME);
        setInvulnerable(self, true);
        setCondition(self, CONDITION_CONVERSABLE);
        setObjVar(self, OBJVAR_MAX_BUDGET_20, true);
    }
    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        int mnu = mi.addRootMenu(menu_info_types.CONVERSE_START, null);
        menu_info_data mdata = mi.getMenuItemById(mnu);
        mdata.setServerNotify(false);
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }
    public int OnStartNpcConversation(obj_id self, obj_id speaker) throws InterruptedException
    {
        if (isIdValid(speaker) && isPlayer(speaker))
        {
            startBuffBuilder(self, speaker);
        }
        return SCRIPT_CONTINUE;
    }
    public void startBuffBuilder(obj_id self, obj_id player) throws InterruptedException
    {
        if (isDead(player) || isIncapacitated(player))
        {
            sendSystemMessageTestingOnly(player, "You cannot receive inspiration in your current condition.");
            return;
        }
        if (getDistance(self, player) > MAX_USE_RANGE)
        {
            sendSystemMessageTestingOnly(player, "You are too far away from the entertainer.");
            return;
        }
        if (hasScript(player, SCRIPT_BUFF_BUILDER_CANCEL))
        {
            sendSystemMessage(player, new string_id("spam", "already_being_inspired"));
            return;
        }
        // Prime the inspiration duration for this session. buff_builder_response
        // reads performance.inspiration on Accept to size the buff; clear any
        // stale value first so we always apply the NPC's fixed one-hour duration.
        if (utils.hasScriptVar(player, performance.VAR_PERFORM_INSPIRATION))
        {
            utils.removeScriptVar(player, performance.VAR_PERFORM_INSPIRATION);
        }
        utils.setScriptVar(player, performance.VAR_PERFORM_INSPIRATION, INSPIRATION_DURATION);
        // Track the player so OnBuffBuilderCanceled can clean up the primed value.
        utils.setScriptVar(self, SCRIPTVAR_PID, player);
        // Open the real Build-a-Buff window (T-010 hook): attach the response
        // handler to the NPC (the buffer) and the cancel handler to the player
        // (the recipient), then start the session.
        attachScript(self, SCRIPT_BUFF_BUILDER_RESPONSE);
        attachScript(player, SCRIPT_BUFF_BUILDER_CANCEL);
        buffBuilderStart(self, player);
    }
    public int OnBuffBuilderCanceled(obj_id self) throws InterruptedException
    {
        // The player closed the window without accepting. Remove the primed
        // duration so it does not linger. This handler only fires on the cancel
        // path, never on the accept path, so buff_builder_response is free to
        // consume the value there.
        if (utils.hasScriptVar(self, SCRIPTVAR_PID))
        {
            obj_id player = utils.getObjIdScriptVar(self, SCRIPTVAR_PID);
            if (isIdValid(player) && exists(player))
            {
                if (utils.hasScriptVar(player, performance.VAR_PERFORM_INSPIRATION))
                {
                    utils.removeScriptVar(player, performance.VAR_PERFORM_INSPIRATION);
                }
            }
            utils.removeScriptVar(self, SCRIPTVAR_PID);
        }
        return SCRIPT_CONTINUE;
    }
}
