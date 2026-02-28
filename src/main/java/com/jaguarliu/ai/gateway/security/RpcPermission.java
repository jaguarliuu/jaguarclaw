package com.jaguarliu.ai.gateway.security;

/**
 * RPC 权限级别
 */
public enum RpcPermission {
    PUBLIC,
    READ,
    WRITE,
    CONFIG,
    DANGEROUS,
    ADMIN
}

