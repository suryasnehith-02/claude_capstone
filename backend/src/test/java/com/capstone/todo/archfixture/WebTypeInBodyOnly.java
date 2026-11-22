package com.capstone.todo.archfixture;

import com.capstone.todo.web.response.ErrorCode;

/** A deliberately bad "domain" class: a web type used only inside a method body. Test scope only. */
public class WebTypeInBodyOnly {

    public String describe() {
        return ErrorCode.INTERNAL_ERROR.code();
    }
}
