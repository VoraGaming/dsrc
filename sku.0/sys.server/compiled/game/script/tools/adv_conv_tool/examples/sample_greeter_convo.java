package script.tools.adv_conv_tool.examples;

import script.obj_id;
import script.tools.adv_conv_tool.advanced_conversation_tool;

/**
 * Second example for the advanced conversation tool: a two-step conversation (uses the npcSpeak continuation path)
 * that keeps the default conversation id and branch key (derived from the class name).
 * Used to check that two tool-based conversation scripts can run side by side.
 * Attach to any NPC: tools.adv_conv_tool.examples.sample_greeter_convo
 */
public class sample_greeter_convo extends advanced_conversation_tool {

    public sample_greeter_convo()
    {
    }

    @Override
    protected void initializeConversationTree(obj_id npc, obj_id player) throws InterruptedException {
        addEntryPoint((_npc, _player) -> true, 1);

        createBranch(1)
                .setNpcAnimation("greet")
                .setNpcMessage("Hello, traveller. Want to hear something?")
                .addResponse("Tell me more.", 2)
                .addExitResponse("Not now.", "Suit yourself.");

        createBranch(2)
                .setNpcAnimation("explain")
                .setNpcMessage("This conversation was built with the advanced conversation tool.")
                .addExitResponse("Thanks.", "Safe travels.", (_npc, _player) -> doAnimationAction(_player, "nod"));
    }
}
