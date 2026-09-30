package script.systems.buff_builder;

import script.*;
import script.library.buff;
import script.library.utils;

public class entertainer_buff_npc extends script.base_script
{
    public entertainer_buff_npc()
    {
    }
    public static final String SCRIPT_BUFF_BUILDER_CANCEL = "systems.buff_builder.buff_builder_cancel";
    public static final String BUILDABUFF_NAME = "buildabuff_inspiration";
    public static final float BUFF_DURATION = 3600.0f;
    public static final float BUFF_BUILDER_RANGE = 8.0f;
    public static final String SID_ALREADY_BEING_INSPIRED = "already_being_inspired";
    public static final String[] ENTERTAINER_EXPERTISE_SKILLS =
    {
        "expertise_en_inspired_fitness_1",
        "expertise_en_inspired_fitness_2",
        "expertise_en_inspired_fitness_3",
        "expertise_en_inspired_fitness_4",
        "expertise_en_inspired_resilience_1",
        "expertise_en_inspired_resilience_2",
        "expertise_en_inspired_resilience_3",
        "expertise_en_inspired_resilience_4",
        "expertise_en_inspired_industry_1",
        "expertise_en_inspired_industry_2",
        "expertise_en_inspired_industry_3",
        "expertise_en_inspired_industry_4",
        "expertise_en_creativity_1",
        "expertise_en_creativity_2",
        "expertise_en_creativity_3",
        "expertise_en_creativity_4",
        "expertise_en_intense_performer_1",
        "expertise_en_intense_performer_2",
        "expertise_en_intense_performer_3",
        "expertise_en_intense_performer_4",
        "expertise_en_lasting_impression_1",
        "expertise_en_lasting_impression_2",
        "expertise_en_lasting_impression_3",
        "expertise_en_lasting_impression_4",
        "expertise_en_inspired_reactions_1",
        "expertise_en_inspired_reactions_2",
        "expertise_en_inspired_reactions_3",
        "expertise_en_inspired_reactions_4",
        "expertise_en_inspired_warfare_1",
        "expertise_en_inspired_warfare_2",
        "expertise_en_inspired_warfare_3",
        "expertise_en_inspired_warfare_4"
    };

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        grantEntertainerExpertise(self);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return SCRIPT_OVERRIDE;
        }
        mi.addRootMenu(menu_info_types.ITEM_USE, null);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item != menu_info_types.ITEM_USE)
        {
            return SCRIPT_CONTINUE;
        }
        if (!isIdValid(player) || isDead(player) || isIncapacitated(player))
        {
            return SCRIPT_CONTINUE;
        }
        if (hasScript(player, SCRIPT_BUFF_BUILDER_CANCEL))
        {
            sendSystemMessage(player, new string_id("spam", SID_ALREADY_BEING_INSPIRED));
            return SCRIPT_CONTINUE;
        }
        float distance = (getLocation(self)).distance(getLocation(player));
        if (distance > BUFF_BUILDER_RANGE)
        {
            return SCRIPT_CONTINUE;
        }
        attachScript(player, SCRIPT_BUFF_BUILDER_CANCEL);
        buffBuilderStart(self, player);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderValidate(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        if (isIdValid(recipientId) && hasScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL))
        {
            detachScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL);
        }
        buffBuilderValidated(bufferId, recipientId, startingTime, bufferRequiredCredits, recipientPaidCredits, accepted, buffComponentKeys, buffComponentValues);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCompleted(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        if (!isIdValid(recipientId) || !isIdValid(bufferId))
        {
            if (isIdValid(recipientId) && hasScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL))
            {
                detachScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL);
            }
            return SCRIPT_CONTINUE;
        }
        if ((buffComponentKeys == null) || (buffComponentKeys.length == 0))
        {
            if (hasScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL))
            {
                detachScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL);
            }
            return SCRIPT_CONTINUE;
        }
        utils.setScriptVar(recipientId, "performance.buildabuff.buffComponentKeys", buffComponentKeys);
        utils.setScriptVar(recipientId, "performance.buildabuff.buffComponentValues", buffComponentValues);
        utils.setScriptVar(recipientId, "performance.buildabuff.bufferId", bufferId);
        if (buff.hasBuff(recipientId, BUILDABUFF_NAME))
        {
            buff.removeBuff(recipientId, BUILDABUFF_NAME);
        }
        buff.applyBuff(recipientId, BUILDABUFF_NAME, BUFF_DURATION);
        if (hasScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL))
        {
            detachScript(recipientId, SCRIPT_BUFF_BUILDER_CANCEL);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCanceled(obj_id self) throws InterruptedException
    {
        return SCRIPT_CONTINUE;
    }

    public void grantEntertainerExpertise(obj_id self) throws InterruptedException
    {
        for (String skillName : ENTERTAINER_EXPERTISE_SKILLS)
        {
            if (isIdValid(self) && !hasSkill(self, skillName))
            {
                grantSkill(self, skillName);
            }
        }
    }
}
