package de.mirkosertic.gamecomposer.objectinspector.utils;

import org.controlsfx.control.PropertySheet;
import org.controlsfx.validation.Validator;

public class IntegerPropertyEditor extends AbstractIntegerPropertyEditor {

    public IntegerPropertyEditor(PropertySheet.Item aItem) {
        super(aItem, Validator.combine(
                Validator.createEmptyValidator("A value is required"),
                Validator.createPredicateValidator(new IntegerPredicate(), "Value is not a valid number")
        ));
    }
}