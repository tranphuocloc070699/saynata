package com.englishtranslation.backend.aipicture;

/** Draws one picture from an image prompt. CodexImageDrawer is the real one; the tests use a fake. */
public interface ImageDrawer {

    /**
     * @param imagePrompt the prompt the admin edited (without the Codex wrapper text)
     * @return a PNG as the drawer made it
     * @throws DrawException when no picture was made; the message is for the admin
     */
    byte[] draw(String imagePrompt) throws DrawException;
}
