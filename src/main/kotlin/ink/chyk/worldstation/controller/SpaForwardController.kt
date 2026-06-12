package ink.chyk.worldstation.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SpaForwardController {
    @RequestMapping(
        value = [
            "/",
            "/404",
            "/upload/worldmap",
            "/upload/image",
            "/edit",
        ]
    )
    fun forward(): String {
        return "forward:/index.html"
    }
}
