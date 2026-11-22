package com.capstone.todo.archfixture;

import java.util.List;

import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

/** A deliberately bad "domain" class: U-ARCH must report web types in signatures. Test scope only. */
public class WebTypeInSignature {

    public List<ApiResponses> responses;

    public ErrorCode code() {
        return null;
    }
}
