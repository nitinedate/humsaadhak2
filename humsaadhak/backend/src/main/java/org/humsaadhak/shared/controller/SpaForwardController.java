package org.humsaadhak.shared.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves index.html for client-side routes so deep links survive a page refresh. */
@Controller
public class SpaForwardController {
    @GetMapping({"/", "/{path:^(?!api|actuator|assets|swagger-ui|v3)[^.]*}", "/{path:^(?!api|actuator|assets|swagger-ui|v3)[^.]*}/**"})
    String forward() {
        return "forward:/index.html";
    }
}
