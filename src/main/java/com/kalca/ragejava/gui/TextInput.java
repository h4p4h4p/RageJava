package com.kalca.ragejava.gui;

public interface TextInput {

    boolean isTextCapturing();

    /** True when the coordinates land on the editable region, so outside clicks can commit. */
    boolean hitsTextField(float mx, float my);

    /** Return true when the key was consumed. */
    boolean onTextKey(char typedChar, int keyCode);

    /** Handle mouse click on the text field. Return true if consumed. */
    boolean onClick(float mx, float my, int button);

    void commitTextInput();

    void cancelTextInput();
}
