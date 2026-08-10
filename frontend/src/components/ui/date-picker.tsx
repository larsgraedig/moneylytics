import * as React from 'react'
import { format, isValid } from 'date-fns'
import { de } from 'date-fns/locale'
import { CalendarIcon } from 'lucide-react'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

interface DatePickerProps {
  value: Date | null
  onChange: (date: Date | null) => void
  min?: Date
  max?: Date
  placeholder?: string
  className?: string
}

export function DatePicker({ value, onChange, min, max, placeholder = 'TT.MM.JJJJ', className }: DatePickerProps) {
  const [open, setOpen] = React.useState(false)

  const now = new Date()
  const startMonth = min ?? new Date(2000, 0)
  const endMonth = max ?? new Date(now.getFullYear() + 1, 11)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        className={cn(
          'inline-flex h-9 items-center gap-2 rounded-lg border border-border bg-transparent px-3 text-sm font-normal text-left whitespace-nowrap transition-colors hover:bg-muted',
          !value && 'text-muted-foreground',
          className,
        )}
      >
        <CalendarIcon className="h-4 w-4 shrink-0 opacity-50" />
        {value && isValid(value) ? format(value, 'dd.MM.yyyy', { locale: de }) : placeholder}
      </PopoverTrigger>
      <PopoverContent align="start">
        <Calendar
          mode="single"
          selected={value ?? undefined}
          onSelect={d => { onChange(d ?? null); setOpen(false) }}
          disabled={d => (min ? d < min : false) || (max ? d > max : false)}
          locale={de}
          defaultMonth={value ?? undefined}
          captionLayout="dropdown"
          startMonth={startMonth}
          endMonth={endMonth}
        />
      </PopoverContent>
    </Popover>
  )
}
