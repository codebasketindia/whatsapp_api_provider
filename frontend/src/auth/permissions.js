/** Super admin has every permission; partners have none of the admin permissions. */
export function hasPermission(user, perm) {
  if (!user || user.userType !== 'ADMIN') return false;
  if (!perm) return true;
  if (user.isSuperAdmin) return true;
  const list = Array.isArray(perm) ? perm : [perm];
  return list.some((p) => (user.permissions || []).includes(p));
}
