import { useState } from 'react'
import type { Comparison, Session } from '../../types/contracts'
import { comparisonText } from '../../utils/comparisonText'
import { readSavedPolicies } from '../../stores/savedPolicies'

export default function ExportComparison({ result, session }: { result: Comparison; session: Session }) {
  const [message, setMessage] = useState('')
  const contents = () => comparisonText(session, result, readSavedPolicies(session.sessionId))

  function download() {
    const url = URL.createObjectURL(new Blob([contents()], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = '첫집내비-' + result.comparisonId + '.txt'
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    setMessage('텍스트 파일 다운로드를 시작했어요.')
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(contents())
      setMessage('비교 결과를 복사했어요.')
    } catch {
      setMessage('복사 권한을 확인하거나 텍스트 파일 저장을 이용해 주세요.')
    }
  }

  return <section className="panel scenario"><h2>내 첫 집 파일</h2>
    <div className="actions"><button type="button" onClick={download}>텍스트 파일 저장</button>
      <button type="button" className="secondary" onClick={() => void copy()}>복사</button></div>
    <p role="status">{message}</p>
  </section>
}
