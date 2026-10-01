package script.conversation;

import script.*;
import script.library.ai_lib;

public class entertainer_buff_npc extends script.base_script
{
    public entertainer_buff_npc()
    {
    }
    public static final String SCRIPT_BUFF_BUILDER_CANCEL = "systems.buff_builder.buff_builder_cancel";
    public static final float BUFF_BUILDER_RANGE = 8.0f;
    public static final String SID_ALREADY_BEING_INSPIRED = "already_being_inspired";

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if ((!isTangible(self)) || (isPlayer(self)))
        {
            detachScript(self, "conversation.entertainer_buff_npc");
        }
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
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
        if (hasScript(player, SCRIPT_BUFF_BUILDER_CANCEL))
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
        attachScript(player, SCRIPT_BUFF_BUILDER_CANCEL);
        buffBuilderStart(self, player);
        return SCRIPT_CONTINUE;
    }
}
