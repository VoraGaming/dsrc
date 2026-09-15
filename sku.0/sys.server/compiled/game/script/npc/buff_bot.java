package script.npc;

import script.*;
import script.library.*;

public class buff_bot extends script.base_script
{
    public buff_bot()
    {
    }
    public static final String NPC_NAME = "Buff Bot";
    public static final String TITLE = "Buff Bot";
    public static final String PROMPT = "Select the medical enhancement you would like to receive.";
    public static final float BUFF_DURATION = 3600.0f;
    public static final float MAX_USE_RANGE = 10.0f;
    public static final String SCRIPTVAR_PID = "buff_bot.pid";
    public static final String[] BUFF_NAMES =
    {
        "me_buff_health_2",
        "me_buff_action_3",
        "me_buff_strength_3",
        "me_buff_agility_3",
        "me_buff_precision_3",
        "me_buff_melee_gb_1",
        "me_buff_ranged_gb_1"
    };
    public static final String[] BUFF_LABELS =
    {
        "Enhance Constitution (+250)",
        "Enhance Stamina (+250)",
        "Enhance Strength (+80)",
        "Enhance Agility (+80)",
        "Enhance Precision (+80)",
        "Enhance Block (+10% block chance)",
        "Enhance Dodge (+5% dodge)"
    };
    public static final String ALL_BUFFS_LABEL = "Apply All Medical Enhancements";
    public int OnAttach(obj_id self) throws InterruptedException
    {
        setupBuffBot(self);
        return SCRIPT_CONTINUE;
    }
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setupBuffBot(self);
        return SCRIPT_CONTINUE;
    }
    public void setupBuffBot(obj_id self) throws InterruptedException
    {
        setName(self, NPC_NAME);
        setInvulnerable(self, true);
        setCondition(self, CONDITION_CONVERSABLE);
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
            showBuffMenu(self, speaker);
        }
        return SCRIPT_CONTINUE;
    }
    public void showBuffMenu(obj_id self, obj_id player) throws InterruptedException
    {
        closeOldWindow(player);
        String[] options = new String[BUFF_LABELS.length + 1];
        options[0] = ALL_BUFFS_LABEL;
        System.arraycopy(BUFF_LABELS, 0, options, 1, BUFF_LABELS.length);
        int pid = sui.listbox(self, player, PROMPT, sui.OK_CANCEL, TITLE, options, "handleBuffSelect", true, false);
        if (pid > -1)
        {
            utils.setScriptVar(player, SCRIPTVAR_PID, pid);
        }
    }
    public int handleBuffSelect(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null || params.isEmpty())
        {
            return SCRIPT_CONTINUE;
        }
        obj_id player = sui.getPlayerId(params);
        if (!isIdValid(player))
        {
            return SCRIPT_CONTINUE;
        }
        utils.removeScriptVar(player, SCRIPTVAR_PID);
        int btn = sui.getIntButtonPressed(params);
        int idx = sui.getListboxSelectedRow(params);
        if (btn == sui.BP_CANCEL || idx < 0 || idx > BUFF_NAMES.length)
        {
            return SCRIPT_CONTINUE;
        }
        if (isDead(player) || isIncapacitated(player))
        {
            sendSystemMessageTestingOnly(player, "You cannot receive medical enhancements in your current condition.");
            return SCRIPT_CONTINUE;
        }
        if (getDistance(self, player) > MAX_USE_RANGE)
        {
            sendSystemMessageTestingOnly(player, "You are too far away from the Buff Bot.");
            return SCRIPT_CONTINUE;
        }
        if (idx == 0)
        {
            for (String buffName : BUFF_NAMES) {
                buff.applyBuff(player, buffName, BUFF_DURATION);
            }
            sendSystemMessageTestingOnly(player, "You have received all medical enhancements.");
        }
        else
        {
            buff.applyBuff(player, BUFF_NAMES[idx - 1], BUFF_DURATION);
            sendSystemMessageTestingOnly(player, "You have received: " + BUFF_LABELS[idx - 1]);
        }
        playClientEffectObj(player, "appearance/pt_heal.prt", player, "");
        showBuffMenu(self, player);
        return SCRIPT_CONTINUE;
    }
    public void closeOldWindow(obj_id player) throws InterruptedException
    {
        if (utils.hasScriptVar(player, SCRIPTVAR_PID))
        {
            forceCloseSUIPage(utils.getIntScriptVar(player, SCRIPTVAR_PID));
            utils.removeScriptVar(player, SCRIPTVAR_PID);
        }
    }
}
