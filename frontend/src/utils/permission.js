export function canAccess(role, requiredRoles) {
  if (!requiredRoles || requiredRoles.length === 0) {
    return true
  }
  return requiredRoles.includes(role)
}
