package com.lqr.papermind.auth.dto;

import java.util.List;

public record UpdateRolesRequest(List<String> roles) {
}