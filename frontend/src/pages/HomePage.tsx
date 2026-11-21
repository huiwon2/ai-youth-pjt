import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

const chapters = [
  ['내 조건', '내 상황을 알려 주세요', '보증금과 수입, 자주 가는 곳을 입력해요.'],
  ['받을 지원', '받을 수 있는 돈, 근거까지 보여줘요', '공고문의 조건을 내 조건과 맞춰 보고, 다시 볼 지원은 담아 두세요.'],
  ['집 고르기', '후보 집은 같은 기준으로', '한 달 비용, 출퇴근 시간, 동네 생활을 한 표에서 비교해요.'],
]

type Props = { busy: boolean; error: string; resume: string | null; start: () => void; startEasy: () => void }

export default function HomePage({ busy, error, resume, start, startEasy }: Props) {
  const [scene, setScene] = useState(0)
  const story = useRef<HTMLElement>(null)
  useEffect(() => {
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) if (entry.isIntersecting) setScene(Number((entry.target as HTMLElement).dataset.scene))
    }, { rootMargin: '-40% 0px -40%' })
    story.current?.querySelectorAll('[data-scene]').forEach(el => observer.observe(el))
    return () => observer.disconnect()
  }, [])
  const action = <button className="pillb" onClick={start} disabled={busy}>{busy ? '작업을 여는 중…' : '내 파일 만들기'}</button>
  return <div className="lp">
    <section className="lp-hero">
      <h1>첫 독립,<br />받을 돈부터 챙겨요</h1>
      <p>서울 청년 지원금 계산부터 집 비교, 계약 전 확인까지.<br />질문 몇 개면 내 첫 집 파일이 만들어져요.</p>
      <div className="lp-cta">{action}<button className="lp-easy" disabled={busy} onClick={startEasy}>쉬운 화면으로 볼래요 ›</button></div>
      <p className="lp-note">로그인 없이 바로 써요 · 5분쯤 걸려요</p>
      {resume && <Link to={resume}>이전 작업 이어가기</Link>}
      {error && <p role="alert" className="error">{error}</p>}
      <div className="lp-float" aria-label="결과 예시">
        <article className="fc a"><div className="fk">내년에 받을 수 있는 지원금 <span className="ex">예시</span></div>
          <div className="fv">최대 <b>520</b>만 원</div><div className="fbar"><span /><span /></div>
          <ul className="fl"><li><i />국토부 청년월세<b>480만</b></li><li><i />서울시 중개보수·이사비 지원<b>40만</b></li></ul>
        </article>
        <article className="fc b"><div className="fk">먼저 볼 집 <span className="ex">예시</span></div><ol className="frk">
          {['성수동', '역삼동', '신림동'].map((name, i) => <li key={name} className={i === 2 ? 'u' : ''}><span className="n">{i === 2 ? '–' : i + 1}</span>
            <span className={'slot slot-' + i}>{'ABC'[i]}</span><b>{name}</b>{i === 2 ? <span className="uk">관리비 확인 필요</span> : <><span className="br"><i style={{width: i ? '77%' : '87%'}} /></span><em>{i ? 77 : 87}점</em></>}</li>)}
        </ol></article>
      </div>
    </section>
    <section ref={story} className="lp-story" aria-label="이렇게 써요"><div>{chapters.map(([name,title,desc], i) => <article data-scene={i} key={name} className={'lp-step' + (scene === i ? ' on' : '')}>
      <span className="no">0{i + 1} {name}</span><h2>{title}</h2><p>{desc}</p><img className="mobile-shot" loading="lazy" src={'/guide/chapter-' + (i + 1) + '.png'} alt={name + ' 예시 화면'} />
    </article>)}</div><div className="lp-stick"><img src={'/guide/chapter-' + (scene + 1) + '.png'} alt={chapters[scene][0] + ' 예시 화면'} /></div></section>
    <section className="lp-q"><h2>공고문에 이렇게<br />물어볼 수 있어요</h2><div className="question-strip">{['신청할 때 어떤 서류가 필요한가요?', '언제까지 신청할 수 있나요?', '소득 기준은 어떻게 확인하나요?'].map(q => <span key={q}>{q}</span>)}</div></section>
    <section className="lp-num" aria-label="첫집내비 이용 안내">
      {[['내 조건', '9', '가지', '내 예산과 자주 가는 곳을 한 번에'], ['작업 단계', '3', '단계', '내 조건부터 지원과 집 비교까지'],
        ['후보 집 비교', '3', '곳까지', '비용 · 시간 · 동네를 한 표에'], ['로그인', '0', '번', '가입 없이 바로 시작해요']].map(([label, value, unit, detail]) =>
        <div key={label}><small>{label}</small><b>{value}<i>{unit}</i></b><p>{detail}</p></div>)}
    </section>
    <section className="lp-trust"><h2>숫자는 코드가 계산하고,<br />AI는 근거를 설명해요</h2>
      <div className="lp-tr"><div><b>공고문 원문을 같이</b><p>정책별 판정에서 공고의 조건과 근거를 확인해요.</p></div>
        <div><b>모르면 ‘확인 필요’</b><p>관리비가 확인되지 않은 집은 비교표에 따로 표시해요.</p></div>
        <div><b>같은 기준으로 비교</b><p>후보마다 한 달 주거비와 목적지까지 걸리는 시간을 함께 봐요.</p></div></div>
    </section>
    <section className="lp-end"><h2>내 첫 집 파일,<br />지금 만들어 볼까요?</h2>{action}{error && <p role="alert">{error}</p>}</section>
  </div>
}
