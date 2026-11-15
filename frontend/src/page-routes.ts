import type { Role } from './types'
export const pages: {path: string; title: string; roles?: Role[]}[] = [
  {path: '/', title: 'Landing'}, {path: '/explore', title: 'Public problem explorer'},
  {path: '/challenges/:id', title: 'Challenge detail'},
  {path: '/submit', title: 'Submit challenge', roles: ['CITIZEN']},
  {path: '/notifications', title: 'Notifications', roles: ['CITIZEN','ADMIN','UNIVERSITY','INDUSTRY','GOVERNMENT']},
  {path: '/my-challenges', title: 'My challenges', roles: ['CITIZEN']},
  {path: '/admin/validation', title: 'Validation queue', roles: ['ADMIN']},
  {path: '/admin/allocation', title: 'Allocation', roles: ['ADMIN']},
  {path: '/admin/accounts', title: 'Account provisioning', roles: ['ADMIN']},
  {path: '/universities/:id/departments', title: 'University department profile'},
  {path: '/university/inbox', title: 'University inbox', roles: ['UNIVERSITY']},
  {path: '/projects/:id', title: 'Project workspace', roles: ['UNIVERSITY','ADMIN','GOVERNMENT','INDUSTRY']},
  {path: '/industry', title: 'Industry dashboard', roles: ['INDUSTRY']},
  {path: '/government', title: 'Government analytics', roles: ['GOVERNMENT']},
]
