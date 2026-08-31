import { type ReactNode } from 'react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'

export function GenericModal({
  onClose,
  title,
  children,
  footer,
}: {
  onClose: () => void
  title: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="flex flex-col w-[75vw] max-w-[75vw] sm:max-w-[75vw] max-h-[85vh] overflow-hidden p-0 gap-0">
        <DialogHeader className="border-b px-5 py-4 shrink-0">
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="flex-1 min-h-0 overflow-y-auto">
          {children}
        </div>
        {footer != null && (
          <div className="flex items-center justify-between border-t px-5 py-3 shrink-0">
            {footer}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
