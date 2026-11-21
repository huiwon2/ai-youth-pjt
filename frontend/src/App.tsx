import HomePage from './pages/HomePage'
import { useEffect, useState } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes, useParams, useLocation, useNavigate } from 'react-router'
import { api, sessionKey } from './api/firsthome'
import { ApiError, errorMessage } from './api/errors'
import ProfilePage from './pages/ProfilePage'
import ComparePage from './pages/ComparePage'
import ComparisonPage from './pages/ComparisonPage'
import SupportPage from './pages/SupportPage'
import type { Comparison, Session } from './types/contracts'

function ComparisonRoute({ session, mode, onSave }: { session: Session; mode: string; onSave: (value: Session) => void }) {
  const { comparisonId } = useParams()
  const [recalculated, setRecalculated] = useState<Comparison | null>(null)
  const initialResult = recalculated?.comparisonId === comparisonId && recalculated?.inputRevision === session.revision ? recalculated : null
  return <ComparisonPage key={comparisonId} comparisonId={comparisonId!} session={session} mode={mode}
    onSave={onSave} initialResult={initialResult} onRecalculated={setRecalculated} />
}

function FirstHome() {
  const location = useLocation()
  const navigate = useNavigate()
  const easy = new URLSearchParams(location.search).get('mode') === 'easy'
  const mode = easy ? '?mode=easy' : ''
  const [session, setSession] = useState<Session | null>(null)
  const [restoring, setRestoring] = useState(() => Boolean(sessionStorage.getItem(sessionKey)))
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState('')
  const [restoreError, setRestoreError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const id = sessionStorage.getItem(sessionKey)
    if (!id) return
    let active = true
    api.restore(id).then(value => {
      if (active) setSession(value)
    }).catch(reason => {
      if (!active) return
      if (reason instanceof ApiError && reason.status === 401 && reason.code === 'SESSION_EXPIRED') {
        sessionStorage.removeItem(sessionKey)
        setError('이전 작업을 열 수 없어요. 홈에서 다시 시작해 주세요.')
      } else setRestoreError(errorMessage(reason))
    }).finally(() => {
      if (active) setRestoring(false)
    })
    return () => { active = false }
  }, [attempt])

  async function start(useEasy: boolean) {
    setStarting(true)
    setError('')
    try {
      const value = await api.create()
      sessionStorage.setItem(sessionKey, value.sessionId)
      setSession(value)
      setRestoreError('')
      navigate('/profile' + (useEasy ? '?mode=easy' : ''))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setStarting(false)
    }
  }

  function recover(reason: unknown) {
    if (!(reason instanceof ApiError)) return
    if (reason.status === 401 && reason.code === 'SESSION_EXPIRED') {
      sessionStorage.removeItem(sessionKey)
      setSession(null)
      setError('작업을 다시 시작해 주세요.')
      navigate('/' + mode)
    } else if ([409, 412, 428].includes(reason.status)) {
      setRestoring(true)
      setAttempt(value => value + 1)
    }
  }

  const home = location.pathname === '/'
  const chapters = [
    { path: '/profile', title: '내 조건', detail: '내 상황과 예산' },
    { path: '/support', title: '받을 지원', detail: '지원 금액과 신청 조건' },
    { path: '/compare', title: '집 고르기', detail: '비용 · 시간 · 동네 비교' },
  ]
  const active = chapters.findIndex(item => location.pathname.startsWith(item.path))
  const chapter = chapters[active]
  return (
    <>
      <header className={'topbar' + (home ? ' landing-bar' : '')}><div className="frame header-inner">
        <Link className="brand" to={'/' + mode}>첫집<b>내비</b></Link>
        {!home && chapter && <span className="top-file">내 첫 집 파일 · 0{active + 1} {chapter.title}</span>}
        <Link className="mode-switch" aria-label={easy ? '일반 화면으로 보기' : '쉬운 화면으로 보기'} to={location.pathname + (easy ? '' : '?mode=easy')} replace>쉬운 화면 <span className={easy ? 'sw on' : 'sw'} /></Link>
        {home && <button className="start-small" disabled={starting || restoring} onClick={() => void start(easy)}>시작하기</button>}
      </div></header>
      <main className={home ? 'landing' : 'frame workspace' + (easy ? ' easy-view' : '')}>
        {!home && <nav className="rail" aria-label="내 첫 집 파일 목차"><p>내 첫 집 파일</p>
          {chapters.map((item, i) => <Link key={item.path} to={item.path + mode} aria-current={i === active ? 'step' : undefined}>
            <span className="rail-no">0{i + 1}</span><span>{item.title}<small>{item.detail}</small></span>
          </Link>)}
        </nav>}
        <div className="chapter-content">
        {!home && chapter && <div className="chapter-label"><b>0{active + 1}</b> {chapter.title}</div>}
        <Routes>
          <Route path="/" element={<HomePage busy={starting || restoring} error={error || restoreError} resume={session ? '/profile' + mode : null}
            start={() => void start(easy)} startEasy={() => session ? navigate('/profile?mode=easy') : void start(true)} />} />
          <Route path="/profile" element={restoring ? <p role="status">작업을 불러오는 중…</p> : restoreError ? restoreFailure() : session ?
            <ProfilePage key={session.sessionId + ':' + session.revision} session={session} easy={easy} onSave={value => {
              setSession(value)
              if (value.progress.canEvaluate) navigate('/support' + mode)
            }} onError={recover} /> : <Navigate to={'/' + mode} replace />} />
          <Route path="/support" element={guard(value => <SupportPage key={value.sessionId + ':' + value.revision} session={value} easy={easy} onError={recover} />)} />
          <Route path="/compare" element={guard(value => <ComparePage key={value.sessionId} session={value} mode={mode} onSave={setSession} onError={recover} />)} />
          <Route path="/compare/:comparisonId" element={guard(value => <ComparisonRoute session={value} mode={mode} onSave={saved => setSession(current => current?.sessionId === saved.sessionId && current.revision <= saved.revision ? saved : current)} />)} />
          <Route path="*" element={<Navigate to={'/' + mode} replace />} />
        </Routes>
        </div>
      </main>
      <footer className="file-footer"><div className="frame"><strong>첫집 내비</strong>처음 독립하는 서울 청년을 위한 주거 도우미</div></footer>
    </>
  )

  function guard(render: (value: Session) => React.ReactNode) {
    if (restoring) return <p role="status">작업을 불러오는 중…</p>
    if (restoreError) return restoreFailure()
    if (!session) return <Navigate to={'/' + mode} replace />
    if (!session.progress.canEvaluate) return <Navigate to={'/profile' + mode} replace />
    return render(session)
  }

  function restoreFailure() {
    return <div><p role="alert" className="error">{restoreError}</p><button onClick={() => {
      setRestoreError('')
      setRestoring(true)
      setAttempt(value => value + 1)
    }}>다시 불러오기</button></div>
  }
}

export default function App() {
  return <BrowserRouter><FirstHome /></BrowserRouter>
}
