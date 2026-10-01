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
