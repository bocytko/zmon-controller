package de.zalando.zmon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class GrafanaController {

	@RequestMapping(value = "/grafana")
	public String grafana() {
		return "grafana";
	}

}
