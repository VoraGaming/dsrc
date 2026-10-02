package script.systems.buff_builder;

import script.*;
import script.library.buff;
import script.library.utils;
import script.npc.entertainer_npc_session;

public class entertainer_buff_npc extends script.base_script
{
    public entertainer_buff_npc()
    {
    }
    public static final String SCRIPT_BUFF_BUILDER_CANCEL = "systems.buff_builder.buff_builder_cancel";
    public static final String BUILDABUFF_NAME = "buildabuff_inspiration";
    public static final float BUFF_DURATION = 3600.0f;

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
        entertainer_npc_session.applyMasterMods(self);
    }
}
