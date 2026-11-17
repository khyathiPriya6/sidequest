import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button/Button'
import { Input } from './Input/Input'
import { Card } from './Card/Card'
import { Badge } from './Badge/Badge'

describe('shared controls', () => {
  it('activates a button from the keyboard without submitting its containing form', async () => {
    const user = userEvent.setup()
    const click = vi.fn()
    const submit = vi.fn(event => event.preventDefault())
    render(<form onSubmit={submit}><Button onClick={click}>Continue</Button></form>)
    await user.tab()
    await user.keyboard('{Enter}')
    expect(click).toHaveBeenCalledOnce()
    expect(submit).not.toHaveBeenCalled()
  })

  it('blocks clicks while loading and preserves the accessible action name', async () => {
    const click = vi.fn()
    render(<Button loading onClick={click}>Save draft</Button>)
    const button = screen.getByRole('button', { name: 'Save draft' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(button)
    expect(click).not.toHaveBeenCalled()
  })

  it('connects input labels, hints, and validation errors while accepting controlled text', async () => {
    function Field() {
      const [value, setValue] = useState('')
      return <Input label="Title" hint="Keep it brief" error={value ? undefined : 'Title is required'}
        value={value} onChange={event => setValue(event.target.value)} required />
    }
    render(<Field />)
    const field = screen.getByRole('textbox', { name: 'Title (required)' })
    expect(field).toHaveAccessibleDescription('Keep it brief Title is required')
    expect(field).toHaveAttribute('aria-invalid', 'true')
    await userEvent.type(field, 'An adventure')
    expect(field).toHaveValue('An adventure')
    expect(field).toHaveAccessibleDescription('Keep it brief')
    expect(field).not.toHaveAttribute('aria-invalid', 'true')
  })

  it('keeps repeated input IDs unique and supports read-only values', () => {
    render(<><Input label="First" readOnly value="Saved" /><Input label="Second" disabled /></>)
    expect(screen.getByLabelText('First').id).not.toBe(screen.getByLabelText('Second').id)
    expect(screen.getByLabelText('First')).toHaveAttribute('readonly')
    expect(screen.getByLabelText('Second')).toBeDisabled()
  })

  it('composes cards and labeled badges without making the container an extra control', () => {
    const click = vi.fn()
    render(<Card><h2>Example</h2><Badge tone="success">Completed</Badge><Button onClick={click}>View</Button></Card>)
    expect(screen.getByText('Completed')).toBeVisible()
    expect(screen.getAllByRole('button')).toHaveLength(1)
    fireEvent.click(screen.getByRole('button'))
    expect(click).toHaveBeenCalledOnce()
  })
})
