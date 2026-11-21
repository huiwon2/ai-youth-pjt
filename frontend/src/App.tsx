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
  return (
    <>
      <header className="topbar">
        <div className="frame header-inner">
          <Link className="brand" to={'/' + mode}><span className="brand-icon" aria-hidden="true">⌂</span> 첫집내비</Link>
          {!home && <Link className="text-link" to={location.pathname + (easy ? '' : '?mode=easy')} replace>
            {easy ? '일반 화면으로 보기' : '쉬운 화면으로 보기'}
          </Link>}
        </div>
      </header>
      <main className={easy ? "frame easy-view" : "frame"}>
        {!home && <nav className="steps" aria-label="작업 단계">
          <Link to={'/profile' + mode} aria-current={location.pathname === '/profile' ? 'step' : undefined}>내 조건</Link>
          <span aria-hidden="true">→</span>
          <Link to={'/support' + mode} aria-current={location.pathname === '/support' ? 'step' : undefined}>받을 지원</Link>
          <span aria-hidden="true">→</span><Link to={'/compare' + mode} aria-current={location.pathname.startsWith('/compare') ? 'step' : undefined}>후보 집</Link>
        </nav>}
        <Routes>
          <Route path="/" element={<section className="hero">
            <div>
              <p className="eyebrow">첫 독립을 준비하는 청년을 위한 주거 안내</p>
              <h1>첫 집을 구할 때,<br />내 조건부터.</h1>
              <p className="intro">예산과 자주 가는 곳을 입력하고<br />내 조건에 맞는 주거 지원을 한곳에서 확인해 보세요.</p>
              <div className="actions">
                <button disabled={starting || restoring} onClick={() => void start(easy)}>{starting ? '작업을 여는 중…' : '내 파일 만들기'}</button>
                <button className="secondary" disabled={starting || restoring} onClick={() => {
                  if (session) navigate('/profile?mode=easy')
                  else navigate(easy ? '/' : '/?mode=easy', { replace: true })
                }}>{easy && !session ? '일반 화면으로 볼래요' : '쉬운 화면으로 볼래요'}</button>
                {session && <Link className="text-link" to={'/profile' + mode}>이전 작업 이어가기</Link>}
              </div>
              {error && <p role="alert" className="error">{error}</p>}
            </div>
            <div className="hero-art" aria-hidden="true"><div className="house"><div className="door" /></div><div className="sun" /><div className="ground" /></div>
          </section>} />
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
      </main>
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
