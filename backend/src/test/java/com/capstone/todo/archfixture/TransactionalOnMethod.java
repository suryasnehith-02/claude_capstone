package com.capstone.todo.archfixture;

import org.springframework.transaction.annotation.Transactional;

/** A deliberately bad "domain" class: U-ARCH must report it. Test scope only. */
public class TransactionalOnMethod {

    @Transactional
    public void save() {
    }
}
