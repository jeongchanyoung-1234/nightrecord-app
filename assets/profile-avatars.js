/** assets/img/full 파일명(듀체스만 철자 dutchess) */
export const PROFILE_AVATARS = [
  { id: 'wylder', jobId: 1, name: '추적자', file: 'assets/img/full/wylder.webp' },
  { id: 'guardian', jobId: 2, name: '수호자', file: 'assets/img/full/guardian.webp' },
  { id: 'ironeye', jobId: 3, name: '철의 눈', file: 'assets/img/full/ironeye.webp' },
  { id: 'dutchess', jobId: 4, name: '레이디', file: 'assets/img/full/dutchess.webp' },
  { id: 'raider', jobId: 5, name: '무뢰한', file: 'assets/img/full/raider.webp' },
  { id: 'revenant', jobId: 6, name: '복수자', file: 'assets/img/full/revenant.webp' },
  { id: 'recluse', jobId: 7, name: '은둔자', file: 'assets/img/full/recluse.webp' },
  { id: 'executor', jobId: 8, name: '집행자', file: 'assets/img/full/executor.webp' },
  { id: 'scholar', jobId: 9, name: '학자', file: 'assets/img/full/scholar.webp' },
  { id: 'undertaker', jobId: 10, name: '장의사', file: 'assets/img/full/undertaker.webp' },
];

export function avatarById(id) {
  return PROFILE_AVATARS.find((entry) => entry.id === id) || null;
}
