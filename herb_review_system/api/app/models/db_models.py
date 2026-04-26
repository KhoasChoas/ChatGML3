from __future__ import annotations

from sqlalchemy import Float, ForeignKey, Integer, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class Pharmacist(Base):
    __tablename__ = "pharmacists"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    seq_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    full_name: Mapped[str] = mapped_column(Text, nullable=False)
    employee_id: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    gender: Mapped[str | None] = mapped_column(Text, nullable=True)
    phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    title_rank: Mapped[str | None] = mapped_column(Text, nullable=True)
    job_title: Mapped[str | None] = mapped_column(Text, nullable=True)
    department: Mapped[str | None] = mapped_column(Text, nullable=True)
    password_credential: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_department_director: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    can_submit_error_report: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    imported_at: Mapped[str] = mapped_column(Text, nullable=False)


class Prescription(Base):
    __tablename__ = "prescriptions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    source_seq_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    prescription_no: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    patient_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    patient_gender: Mapped[str | None] = mapped_column(Text, nullable=True)
    patient_age: Mapped[str | None] = mapped_column(Text, nullable=True)
    fee_type: Mapped[str | None] = mapped_column(Text, nullable=True)
    medical_record_no: Mapped[str | None] = mapped_column(Text, nullable=True)
    dept_bed: Mapped[str | None] = mapped_column(Text, nullable=True)
    address: Mapped[str | None] = mapped_column(Text, nullable=True)
    phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    diagnosis: Mapped[str | None] = mapped_column(Text, nullable=True)
    prescribed_at: Mapped[str | None] = mapped_column(Text, nullable=True)
    herb_kind_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    drug_fee: Mapped[float | None] = mapped_column(Float, nullable=True)
    injection_fee: Mapped[float | None] = mapped_column(Float, nullable=True)
    prescribing_doctor: Mapped[str | None] = mapped_column(Text, nullable=True)
    dispensing_pharmacist: Mapped[str | None] = mapped_column(Text, nullable=True)
    reviewing_doctor: Mapped[str | None] = mapped_column(Text, nullable=True)
    imported_at: Mapped[str] = mapped_column(Text, nullable=False)

    items: Mapped[list["PrescriptionItem"]] = relationship(
        "PrescriptionItem",
        back_populates="prescription",
        order_by="PrescriptionItem.line_no",
        cascade="all, delete-orphan",
    )
    review_sessions: Mapped[list["ReviewSession"]] = relationship(
        "ReviewSession",
        back_populates="prescription",
        viewonly=True,
    )


class PrescriptionItem(Base):
    __tablename__ = "prescription_items"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    prescription_id: Mapped[int] = mapped_column(ForeignKey("prescriptions.id", ondelete="CASCADE"), nullable=False)
    line_no: Mapped[int] = mapped_column(Integer, nullable=False)
    herb_name: Mapped[str] = mapped_column(Text, nullable=False)
    dosage: Mapped[str | None] = mapped_column(Text, nullable=True)
    usage_method: Mapped[str | None] = mapped_column(Text, nullable=True)

    prescription: Mapped["Prescription"] = relationship("Prescription", back_populates="items")


class ReviewSession(Base):
    __tablename__ = "review_sessions"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    prescription_id: Mapped[int] = mapped_column(ForeignKey("prescriptions.id", ondelete="CASCADE"), nullable=False)
    created_by_pharmacist_id: Mapped[int] = mapped_column(
        ForeignKey("pharmacists.id"),
        nullable=False,
    )
    status: Mapped[str] = mapped_column(Text, nullable=False, default="draft")
    llm_model_name: Mapped[str | None] = mapped_column(Text, nullable=True, default="ChatGLM3")
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[str] = mapped_column(Text, nullable=False)
    updated_at: Mapped[str] = mapped_column(Text, nullable=False)

    steps: Mapped[list["SessionStep"]] = relationship(
        "SessionStep",
        back_populates="session",
        order_by="SessionStep.step_index",
        cascade="all, delete-orphan",
    )
    prescription: Mapped["Prescription"] = relationship(
        "Prescription",
        back_populates="review_sessions",
        foreign_keys=[prescription_id],
    )

    @property
    def prescription_no(self) -> str | None:
        p = self.prescription
        return str(p.prescription_no) if p is not None else None


class SessionStep(Base):
    __tablename__ = "session_steps"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    session_id: Mapped[str] = mapped_column(ForeignKey("review_sessions.id", ondelete="CASCADE"), nullable=False)
    step_index: Mapped[int] = mapped_column(Integer, nullable=False)
    prescription_item_id: Mapped[int | None] = mapped_column(
        ForeignKey("prescription_items.id"),
        nullable=True,
    )
    expected_herb_name: Mapped[str] = mapped_column(Text, nullable=False)
    image_uri: Mapped[str | None] = mapped_column(Text, nullable=True)
    llm_recognized_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    llm_confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    llm_raw_response: Mapped[str | None] = mapped_column(Text, nullable=True)
    match_status: Mapped[str] = mapped_column(Text, nullable=False, default="pending")
    reviewer_comment: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[str] = mapped_column(Text, nullable=False)
    updated_at: Mapped[str] = mapped_column(Text, nullable=False)

    session: Mapped["ReviewSession"] = relationship("ReviewSession", back_populates="steps")
