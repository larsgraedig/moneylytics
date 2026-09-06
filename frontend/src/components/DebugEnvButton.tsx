import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Terminal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import EnvVarsPage from './EnvVarsPage'

export default function DebugEnvButton() {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    fetch('/local/environment')
      .then(r => setVisible(r.ok))
      .catch(() => setVisible(false))
  }, [])

  if (!visible) return null

  return (
    <>
      <Button
        variant="secondary"
        size="icon"
        title={t('debug.tooltip')}
        className="fixed bottom-4 right-4 z-[100] rounded-full shadow-lg"
        onClick={() => setOpen(true)}
      >
        <Terminal className="h-4 w-4" />
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('debug.title')}</DialogTitle>
          </DialogHeader>
          <EnvVarsPage />
        </DialogContent>
      </Dialog>
    </>
  )
}
