package script.npc;

import script.*;
import script.library.buff;
import script.library.utils;

// Player-side session script for the Twi'lek entertainer buff-builder.
//
// Attached to the PLAYER (by conversation.entertainer_buff_npc) and drives the
// self-buff: the player is both buffer and recipient, so the player's own client
// shows the Build-a-Buff window. On completion the buff is applied to the player
// but its expertise is attributed to the NPC (the player has no expertise), by
// pointing performance.buildabuff.bufferId at the NPC before applying.
public class entertainer_npc_session extends script.base_script
{
    public entertainer_npc_session()
    {
    }

    // Set on the player by the NPC conversation script: which NPC is buffing the player.
    public static final String VAR_NPC_ID = "performance.buildabuff.npcId";
    // True while a Build-a-Buff session is open (used by the NPC to refuse re-entry).
    public static final String VAR_IN_SESSION = "performance.buildabuff.inSession";

    public static final String BUILDABUFF_NAME = "buildabuff_inspiration";
    public static final float BUFF_DURATION = 3600.0f;

    public int OnBuffBuilderValidate(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        // Self-buff: the player is both buffer and recipient.
        if (!bufferId.equals(self) || !recipientId.equals(self))
        {
            return SCRIPT_OVERRIDE;
        }
        buffBuilderValidated(bufferId, recipientId, startingTime, bufferRequiredCredits, recipientPaidCredits, accepted, buffComponentKeys, buffComponentValues);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCompleted(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        utils.setScriptVar(self, VAR_IN_SESSION, false);
        if (!bufferId.equals(self) || !recipientId.equals(self))
        {
            return SCRIPT_CONTINUE;
        }
        if ((buffComponentKeys == null) || (buffComponentKeys.length == 0))
        {
            return SCRIPT_CONTINUE;
        }

        // Attribute the buff's expertise to the NPC: the player is the buffer but
        // has no expertise, so the buildabuff handler must read the mods from the NPC.
        obj_id npc = utils.getObjIdScriptVar(self, VAR_NPC_ID);
        if (!isIdValid(npc))
        {
            npc = bufferId;
        }

        utils.setScriptVar(self, "performance.buildabuff.buffComponentKeys", buffComponentKeys);
        utils.setScriptVar(self, "performance.buildabuff.buffComponentValues", buffComponentValues);
        utils.setScriptVar(self, "performance.buildabuff.bufferId", npc);
        if (buff.hasBuff(self, BUILDABUFF_NAME))
        {
            buff.removeBuff(self, BUILDABUFF_NAME);
        }
        buff.applyBuff(self, BUILDABUFF_NAME, BUFF_DURATION);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCanceled(obj_id self) throws InterruptedException
    {
        utils.setScriptVar(self, VAR_IN_SESSION, false);
        return SCRIPT_CONTINUE;
    }
}
